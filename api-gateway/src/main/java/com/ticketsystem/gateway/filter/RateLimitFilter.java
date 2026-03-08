package com.ticketsystem.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.gateway.model.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.max-requests}")
    private int maxRequests;

    @Value("${app.rate-limit.window-seconds}")
    private long windowSeconds;

    // Sliding window log algorithm using Redis ZSET
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local max_requests = tonumber(ARGV[3])
            local request_id = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window_ms)
            local count = redis.call('ZCARD', key)
            if count >= max_requests then
                return 0
            end
            redis.call('ZADD', key, now, request_id)
            redis.call('EXPIRE', key, math.ceil(window_ms / 1000))
            return 1
            """, Long.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = getClientIp(exchange.getRequest());
        String key = "rate_limit:" + ip;
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        String requestId = now + ":" + UUID.randomUUID();

        return redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key),
                        String.valueOf(now),
                        String.valueOf(windowMs),
                        String.valueOf(maxRequests),
                        requestId)
                .next()
                .defaultIfEmpty(0L)
                .onErrorReturn(1L)  // fail-open: allow request if Redis is unavailable
                .flatMap(result -> {
                    if (result == 0L) {
                        return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS,
                                "RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.");
                    }
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return -2;
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
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
