package com.ticketsystem.auth.filter;

import com.ticketsystem.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractCookie(request, "access_token");

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // when user logout but access_token still valid
                if (redisTemplate.hasKey("blacklist:" + token)) {
                    chain.doFilter(request, response);
                    return;
                }

                Claims claims = jwtService.validateAndParseClaims(token);
                String principal = claims.getSubject() + ":" + claims.get("actor_type", String.class);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception ignored) {
                // Invalid token — continue without setting authentication
            }
        }

        chain.doFilter(request, response);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
