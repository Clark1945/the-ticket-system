package com.ticketsystem.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.gateway.model.ErrorResponse;
import com.ticketsystem.gateway.service.JwtService;
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
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // method name → ant path patterns that skip JWT verification
    private static final Map<String, List<String>> WHITELIST = Map.of(
            "POST", List.of(
                    "/auth/merchant/register",
                    "/auth/merchant/login",
                    "/auth/merchant/otp/resend",
                    "/auth/merchant/email-verify/**",
                    "/auth/token/refresh",   // refresh_token is used here, not access_token
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

        String token = extractCookie(request, "access_token");
        if (token == null) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "TOKEN_INVALID", "Missing or invalid token");
        }

        return redisTemplate.hasKey("blacklist:" + token)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return writeError(exchange, HttpStatus.UNAUTHORIZED,
                                "TOKEN_INVALID", "Token has been revoked");
                    }
                    try {
                        Claims claims = jwtService.validateAndParseClaims(token);
                        String actorId = jwtService.getActorId(claims);
                        String actorType = jwtService.getActorType(claims);

                        ServerHttpRequest mutated = request.mutate()
                                .header("X-Actor-Id", actorId)
                                .header("X-Actor-Type", actorType)
                                .build();

                        return chain.filter(exchange.mutate().request(mutated).build());
                    } catch (Exception e) {
                        return writeError(exchange, HttpStatus.UNAUTHORIZED,
                                "TOKEN_INVALID", "Invalid or expired token");
                    }
                });
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
