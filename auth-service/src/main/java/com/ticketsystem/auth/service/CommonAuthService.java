package com.ticketsystem.auth.service;

import com.ticketsystem.auth.entity.MerchantRefreshToken;
import com.ticketsystem.auth.entity.UserRefreshToken;
import com.ticketsystem.auth.enums.ActorType;
import com.ticketsystem.auth.enums.AuditAction;
import com.ticketsystem.auth.exception.BusinessException;
import com.ticketsystem.auth.exception.ErrorCode;
import com.ticketsystem.auth.repository.MerchantRefreshTokenRepository;
import com.ticketsystem.auth.repository.UserRefreshTokenRepository;
import com.ticketsystem.auth.util.CookieUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class CommonAuthService {

    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final MerchantRefreshTokenRepository merchantRefreshTokenRepo;
    private final UserRefreshTokenRepository userRefreshTokenRepo;
    private final AuditLogService auditLogService;

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = extractCookie(request, "access_token");
        String refreshToken = extractCookie(request, "refresh_token");

        if (accessToken != null) {
            try {
                Claims claims = jwtService.validateAndParseClaims(accessToken);
                long remaining = jwtService.getRemainingSeconds(claims);
                if (remaining > 0) {
                    redisTemplate.opsForValue()
                            .set("blacklist:" + accessToken, "1", remaining, TimeUnit.SECONDS);
                }

                Long actorId = jwtService.getActorId(claims);
                ActorType actorType = jwtService.getActorType(claims);

                if (refreshToken != null) {
                    if (actorType == ActorType.MERCHANT) {
                        merchantRefreshTokenRepo.findByRefreshToken(refreshToken)
                                .ifPresent(merchantRefreshTokenRepo::delete);
                    } else {
                        userRefreshTokenRepo.findByRefreshToken(refreshToken)
                                .ifPresent(userRefreshTokenRepo::delete);
                    }
                }

                auditLogService.log(actorId, actorType, AuditAction.LOGOUT,
                        request.getRemoteAddr(), null);

            } catch (Exception ignored) {
                // If token is already invalid, still clear cookies
            }
        }

        response.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.clearAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.clearRefreshTokenCookie().toString());
    }

    public void refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refresh_token");
        if (refreshToken == null) {
            throw BusinessException.unauthorized(ErrorCode.TOKEN_INVALID);
        }

        Claims claims;
        try {
            claims = jwtService.validateAndParseClaims(refreshToken);
        } catch (Exception e) {
            throw BusinessException.unauthorized(ErrorCode.TOKEN_INVALID);
        }

        Long actorId = jwtService.getActorId(claims);
        ActorType actorType = jwtService.getActorType(claims);

        if (actorType == ActorType.MERCHANT) {
            MerchantRefreshToken stored = merchantRefreshTokenRepo.findByRefreshToken(refreshToken)
                    .orElseThrow(() -> BusinessException.unauthorized(ErrorCode.TOKEN_INVALID));

            if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
                merchantRefreshTokenRepo.delete(stored);
                throw BusinessException.unauthorized(ErrorCode.TOKEN_INVALID);
            }

            String newRefreshToken = jwtService.generateRefreshToken(actorId, actorType);
            stored.setRefreshToken(newRefreshToken);
            stored.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiry()));
            merchantRefreshTokenRepo.save(stored);

            String newAccessToken = jwtService.generateAccessToken(actorId, actorType);
            setTokenCookies(response, newAccessToken, newRefreshToken);

        } else {
            UserRefreshToken stored = userRefreshTokenRepo.findByRefreshToken(refreshToken)
                    .orElseThrow(() -> BusinessException.unauthorized(ErrorCode.TOKEN_INVALID));

            if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
                userRefreshTokenRepo.delete(stored);
                throw BusinessException.unauthorized(ErrorCode.TOKEN_INVALID);
            }

            String newRefreshToken = jwtService.generateRefreshToken(actorId, actorType);
            stored.setRefreshToken(newRefreshToken);
            stored.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiry()));
            userRefreshTokenRepo.save(stored);

            String newAccessToken = jwtService.generateAccessToken(actorId, actorType);
            setTokenCookies(response, newAccessToken, newRefreshToken);
        }

        auditLogService.log(actorId, actorType, AuditAction.TOKEN_REFRESH,
                request.getRemoteAddr(), null);
    }

    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.createAccessTokenCookie(accessToken, jwtService.getAccessTokenExpiry()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.createRefreshTokenCookie(refreshToken, jwtService.getRefreshTokenExpiry()).toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
