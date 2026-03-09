package com.ticketsystem.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.gateway.model.ErrorResponse;
import com.ticketsystem.gateway.service.JwtService;
import com.ticketsystem.gateway.service.JwtService.TokenStatus;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final Map<String, List<String>> WHITELIST = Map.of(
            "POST", List.of(
                    "/auth/merchant/register",
                    "/auth/merchant/login",
                    "/auth/merchant/otp/resend",
                    "/auth/merchant/email-verify/**",
                    "/auth/token/refresh",
                    "/app/merchant/register",
                    "/app/merchant/login",
                    "/app/merchant/email-verify/**",
                    "/app/merchant/otp/resend",
                    "/app/user/login"
            ),
            "GET", List.of(
                    "/auth/oauth2/**",
                    "/app/merchant/login",
                    "/app/merchant/register",
                    "/app/merchant/email-verify",
                    "/app/user/login",
                    "/app/oauth2/callback",
                    "/images/**"
            )
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (isWhitelisted(request)) {
            return chain.filter(exchange);
        }

        String accessToken = extractCookie(request, "access_token");
        TokenStatus status = accessToken != null
                ? jwtService.getTokenStatus(accessToken)
                : TokenStatus.INVALID;

        if (status == TokenStatus.VALID) {
            return processValidToken(exchange, chain, accessToken);
        }

        if (status == TokenStatus.EXPIRED) {
            String refreshToken = extractCookie(request, "refresh_token");
            if (refreshToken != null) {
                return attemptRefresh(exchange, chain, refreshToken, request.getPath().value());
            }
        }

        // token missing, invalid, or expired with no refresh_token
        return redirectToLogin(exchange, request.getPath().value());
    }

    private Mono<Void> processValidToken(ServerWebExchange exchange, GatewayFilterChain chain,
                                          String token) {
        return redisTemplate.hasKey("blacklist:" + token)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return redirectToLogin(exchange, exchange.getRequest().getPath().value());
                    }
                    try {
                        Claims claims = jwtService.validateAndParseClaims(token);
                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .header("X-Actor-Id", jwtService.getActorId(claims))
                                .header("X-Actor-Type", jwtService.getActorType(claims))
                                .build();
                        return chain.filter(exchange.mutate().request(mutated).build());
                    } catch (Exception e) {
                        return redirectToLogin(exchange, exchange.getRequest().getPath().value());
                    }
                });
    }

    private Mono<Void> attemptRefresh(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String refreshToken, String originalPath) {
        return webClientBuilder.build()
                .post()
                .uri("http://localhost:8081/auth/token/refresh")
                .cookie("refresh_token", refreshToken)
                .retrieve()
                .toBodilessEntity()
                .flatMap(response -> {
                    // Forward new cookies to browser
                    List<String> setCookieHeaders = response.getHeaders().get("Set-Cookie");
                    if (setCookieHeaders != null) {
                        setCookieHeaders.forEach(c ->
                                exchange.getResponse().getHeaders().add("Set-Cookie", c));
                    }

                    // Extract new access_token to continue current request
                    String newAccessToken = extractTokenFromSetCookie(setCookieHeaders);
                    if (newAccessToken == null) {
                        return redirectToLogin(exchange, originalPath);
                    }

                    try {
                        Claims claims = jwtService.validateAndParseClaims(newAccessToken);
                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .header("X-Actor-Id", jwtService.getActorId(claims))
                                .header("X-Actor-Type", jwtService.getActorType(claims))
                                .build();
                        log.info("Token refreshed transparently for path: {}", originalPath);
                        return chain.filter(exchange.mutate().request(mutated).build());
                    } catch (Exception e) {
                        return redirectToLogin(exchange, originalPath);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Token refresh failed: {}", e.getMessage());
                    return redirectToLogin(exchange, originalPath);
                });
    }

    private Mono<Void> redirectToLogin(ServerWebExchange exchange, String path) {
        // API paths return JSON 401
        if (path.startsWith("/auth/")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "TOKEN_INVALID", "Missing or invalid token");
        }
        // Frontend paths redirect to appropriate login page
        String loginUrl = path.startsWith("/app/user/") ? "/app/user/login" : "/app/merchant/login";
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(loginUrl));
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isWhitelisted(ServerHttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        List<String> patterns = WHITELIST.get(method);
        if (patterns == null) return false;
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private String extractCookie(ServerHttpRequest request, String name) {
        List<HttpCookie> cookies = request.getCookies().get(name);
        if (cookies == null || cookies.isEmpty()) return null;
        return cookies.get(0).getValue();
    }

    private String extractTokenFromSetCookie(List<String> setCookieHeaders) {
        if (setCookieHeaders == null) return null;
        for (String header : setCookieHeaders) {
            if (header.startsWith("access_token=")) {
                return header.split(";")[0].substring("access_token=".length());
            }
        }
        return null;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status,
                                   String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ErrorResponse.of(code, message));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to write error response", e);
            return response.setComplete();
        }
    }
}
