package com.ticketsystem.frontend.interceptor;

import com.ticketsystem.frontend.model.SessionData;
import com.ticketsystem.frontend.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class PageGuardInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String path = request.getRequestURI();

        // ------------------------------------------------------------------ //
        //  Public paths — always allow
        // ------------------------------------------------------------------ //
        if (path.equals("/app/oauth2/callback")) {
            return true;
        }

        SessionData session = sessionService.getSession(request);
        boolean loggedIn = (session != null && session.isLoggedIn());

        // ------------------------------------------------------------------ //
        //  Merchant paths
        // ------------------------------------------------------------------ //
        if (path.startsWith("/app/merchant/")) {

            // Public merchant pages that do NOT require authentication
            boolean isMerchantPublic = path.equals("/app/merchant/register")
                || path.equals("/app/merchant/login")
                || path.startsWith("/app/merchant/email-verify");

            // Already logged-in MERCHANT tries to access register or login → home
            if (loggedIn && session.isMerchant() && (
                    path.equals("/app/merchant/login") ||
                    path.equals("/app/merchant/register"))) {
                response.sendRedirect("/app/merchant/home");
                return false;
            }

            // A USER tries to access a protected merchant page → merchant login
            if (loggedIn && session.isUser() && !isMerchantPublic) {
                response.sendRedirect("/app/merchant/login");
                return false;
            }

            // Unauthenticated user tries to access a protected merchant page → login
            if (!loggedIn && !isMerchantPublic) {
                response.sendRedirect("/app/merchant/login");
                return false;
            }

            // email-verify requires pending_email in session
            if (path.startsWith("/app/merchant/email-verify")) {
                // Allow POST (form submit) through without the pending_email check;
                // only block GET if pending_email is absent.
                if ("GET".equalsIgnoreCase(request.getMethod())) {
                    String pendingEmail = sessionService.getPendingEmail(request);
                    if (pendingEmail == null || pendingEmail.isBlank()) {
                        response.sendRedirect("/app/merchant/register");
                        return false;
                    }
                }
            }

            return true;
        }

        // ------------------------------------------------------------------ //
        //  User paths
        // ------------------------------------------------------------------ //
        if (path.startsWith("/app/user/")) {

            boolean isUserPublic = path.equals("/app/user/login");

            // Already logged-in USER tries to access login → home
            if (loggedIn && session.isUser() && path.equals("/app/user/login")) {
                response.sendRedirect("/app/user/home");
                return false;
            }

            // A MERCHANT tries to access a protected user page → user login
            if (loggedIn && session.isMerchant() && !isUserPublic) {
                response.sendRedirect("/app/user/login");
                return false;
            }

            // Unauthenticated user tries to access a protected user page → login
            if (!loggedIn && !isUserPublic) {
                response.sendRedirect("/app/user/login");
                return false;
            }

            return true;
        }

        // Allow all other /app/** paths by default
        return true;
    }
}
