package com.ticketsystem.auth.service;

import com.ticketsystem.auth.entity.AppUser;
import com.ticketsystem.auth.entity.UserAuthProvider;
import com.ticketsystem.auth.entity.UserRefreshToken;
import com.ticketsystem.auth.enums.ActorType;
import com.ticketsystem.auth.enums.AuditAction;
import com.ticketsystem.auth.enums.OAuthProvider;
import com.ticketsystem.auth.enums.UserStatus;
import com.ticketsystem.auth.exception.BusinessException;
import com.ticketsystem.auth.exception.ErrorCode;
import com.ticketsystem.auth.repository.AppUserRepository;
import com.ticketsystem.auth.repository.UserAuthProviderRepository;
import com.ticketsystem.auth.repository.UserRefreshTokenRepository;
import com.ticketsystem.auth.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class UserAuthService {

    private final AppUserRepository userRepository;
    private final UserAuthProviderRepository authProviderRepository;
    private final UserRefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    public void processOAuth2Login(OAuthProvider provider, String providerUid,
                                   String name, String email,
                                   HttpServletRequest request, HttpServletResponse response) {

        // Find existing auth provider or create new user
        AppUser user = authProviderRepository.findByProviderAndProviderUid(provider, providerUid)
                .map(UserAuthProvider::getUser)
                .orElseGet(() -> createUser(name, email));

        if (user.getStatus() == UserStatus.BANNED) {
            throw BusinessException.forbidden(ErrorCode.USER_BANNED);
        }

        // Update or create the auth provider record
        UserAuthProvider authProvider = authProviderRepository
                .findByProviderAndProviderUid(provider, providerUid)
                .orElseGet(() -> {
                    UserAuthProvider ap = new UserAuthProvider();
                    ap.setUser(user);
                    ap.setProvider(provider);
                    ap.setProviderUid(providerUid);
                    return ap;
                });
        authProviderRepository.save(authProvider);

        // Issue tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), ActorType.USER);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), ActorType.USER);

        UserRefreshToken urt = new UserRefreshToken();
        urt.setUser(user);
        urt.setRefreshToken(refreshToken);
        urt.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry));
        refreshTokenRepository.save(urt);

        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.createAccessTokenCookie(accessToken, jwtService.getAccessTokenExpiry()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.createRefreshTokenCookie(refreshToken, refreshTokenExpiry).toString());

        auditLogService.log(user.getId(), ActorType.USER, AuditAction.LOGIN,
                request.getRemoteAddr(), Map.of("provider", provider.name()));
    }

    private AppUser createUser(String name, String email) {
        AppUser user = new AppUser();
        user.setName(name);
        user.setEmail(email);
        return userRepository.save(user);
    }
}
