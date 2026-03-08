package com.ticketsystem.frontend.controller;

import com.ticketsystem.frontend.dto.MeResponse;
import com.ticketsystem.frontend.model.SessionData;
import com.ticketsystem.frontend.service.AuthClientService;
import com.ticketsystem.frontend.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserController {

    private final AuthClientService authClientService;
    private final SessionService    sessionService;

    // ------------------------------------------------------------------ //
    //  Login
    // ------------------------------------------------------------------ //

    @GetMapping("/app/user/login")
    public String loginPage(Model model) {
        // OAuth2 links go through the API Gateway at port 8080
        model.addAttribute("googleOAuthUrl",  "http://localhost:8080/auth/oauth2/google");
        model.addAttribute("lineOAuthUrl",    "http://localhost:8080/auth/oauth2/line");
        model.addAttribute("githubOAuthUrl",  "http://localhost:8080/auth/oauth2/github");
        return "user/login";
    }

    // ------------------------------------------------------------------ //
    //  OAuth2 Callback
    // ------------------------------------------------------------------ //

    /**
     * Called after a successful OAuth2 login via the Auth Service.
     * The API Gateway has already validated the JWT and the browser now holds
     * an access_token cookie set by the Auth Service.
     *
     * Flow:
     *  1. Extract access_token cookie from the incoming request.
     *  2. Call GET /auth/me to resolve actor details.
     *  3. Create a Spring Session backed by Redis.
     *  4. Redirect to /app/user/home.
     */
    @GetMapping("/app/oauth2/callback")
    public String oauth2Callback(HttpServletRequest request, HttpServletResponse response) {
        try {
            String accessToken = authClientService.extractCookieValue(request, "access_token");
            if (accessToken == null || accessToken.isBlank()) {
                log.warn("OAuth2 callback: no access_token cookie found");
                return "redirect:/app/user/login?error=oauth_failed";
            }

            MeResponse me = authClientService.callMe(accessToken);
            sessionService.createSession(request, SessionData.builder()
                .actorId(me.getActorId())
                .actorType(me.getActorType())
                .actorName(me.getActorName())
                .email(me.getEmail())
                .build());

            return "redirect:/app/user/home";
        } catch (Exception e) {
            log.error("OAuth2 callback failed: {}", e.getMessage());
            return "redirect:/app/user/login?error=oauth_failed";
        }
    }

    // ------------------------------------------------------------------ //
    //  Home
    // ------------------------------------------------------------------ //

    @GetMapping("/app/user/home")
    public String homePage(HttpServletRequest request, Model model) {
        SessionData session = sessionService.getSession(request);
        model.addAttribute("actorName", session != null ? session.getActorName() : "");
        model.addAttribute("email",     session != null ? session.getEmail()     : "");
        return "user/home";
    }

    // ------------------------------------------------------------------ //
    //  Logout
    // ------------------------------------------------------------------ //

    @PostMapping("/app/user/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken  = authClientService.extractCookieValue(request, "access_token");
        String refreshToken = authClientService.extractCookieValue(request, "refresh_token");
        authClientService.callLogout(accessToken, refreshToken, response);
        sessionService.clearSession(request);
        return "redirect:/app/user/login";
    }
}
