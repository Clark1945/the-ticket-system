package com.ticketsystem.auth.oauth2;

import com.ticketsystem.auth.enums.OAuthProvider;
import com.ticketsystem.auth.service.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserAuthService userAuthService;

    @Value("${app.bff.redirect-url}")
    private String bffRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId().toUpperCase();
        OAuth2User oAuth2User = token.getPrincipal();

        OAuthProvider provider;
        try {
            provider = OAuthProvider.valueOf(registrationId);
        } catch (IllegalArgumentException e) {
            log.error("Unknown OAuth2 provider: {}", registrationId);
            response.sendRedirect(bffRedirectUrl + "?error=unknown_provider");
            return;
        }

        String providerUid = extractProviderUid(provider, oAuth2User);
        String name = extractName(provider, oAuth2User);
        String email = extractEmail(provider, oAuth2User);

        if (providerUid == null) {
            log.error("Could not extract provider UID for provider {}", provider);
            response.sendRedirect(bffRedirectUrl + "?error=auth_failed");
            return;
        }

        try {
            userAuthService.processOAuth2Login(provider, providerUid, name, email, request, response);
            response.sendRedirect(bffRedirectUrl);
        } catch (Exception e) {
            log.error("OAuth2 login processing failed: {}", e.getMessage());
            response.sendRedirect(bffRedirectUrl + "?error=auth_failed");
        }
    }

    private String extractProviderUid(OAuthProvider provider, OAuth2User user) {
        return switch (provider) {
            case GOOGLE -> user.getAttribute("sub");
            case LINE -> user.getAttribute("userId");
            // GitHub returns id as Integer
            case GITHUB -> String.valueOf(user.getAttribute("id"));
        };
    }

    private String extractName(OAuthProvider provider, OAuth2User user) {
        return switch (provider) {
            case GOOGLE -> user.getAttribute("name");
            case LINE -> user.getAttribute("displayName");
            // GitHub: prefer name, fall back to login (username)
            case GITHUB -> {
                String name = user.getAttribute("name");
                yield (name != null && !name.isBlank()) ? name : user.getAttribute("login");
            }
        };
    }

    private String extractEmail(OAuthProvider provider, OAuth2User user) {
        return switch (provider) {
            case GOOGLE -> user.getAttribute("email");
            // LINE basic profile does not include email
            case LINE -> null;
            // GitHub email is null if user set it to private; handle gracefully
            case GITHUB -> user.getAttribute("email");
        };
    }
}
