package com.ticketsystem.auth.service;

import com.ticketsystem.auth.enums.ActorType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    public String generateAccessToken(Long actorId, ActorType actorType) {
        return Jwts.builder()
                .subject(actorId.toString())
                .claim("actor_type", actorType.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(Long actorId, ActorType actorType) {
        return Jwts.builder()
                .subject(actorId.toString())
                .claim("actor_type", actorType.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiry * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims validateAndParseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getActorId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public ActorType getActorType(Claims claims) {
        return ActorType.valueOf(claims.get("actor_type", String.class));
    }

    public long getRemainingSeconds(Claims claims) {
        long remaining = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000L;
        return Math.max(0, remaining);
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
