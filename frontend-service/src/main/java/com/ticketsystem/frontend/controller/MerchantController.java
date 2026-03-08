package com.ticketsystem.frontend.controller;

import com.ticketsystem.frontend.dto.AuthVerifyResult;
import com.ticketsystem.frontend.dto.MeResponse;
import com.ticketsystem.frontend.dto.MerchantRegisterForm;
import com.ticketsystem.frontend.model.SessionData;
import com.ticketsystem.frontend.service.AuthClientService;
import com.ticketsystem.frontend.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/app/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final AuthClientService authClientService;
    private final SessionService    sessionService;

    // ------------------------------------------------------------------ //
    //  Register
    // ------------------------------------------------------------------ //

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", new MerchantRegisterForm());
        return "merchant/register";
    }

    @PostMapping("/register")
    public String registerSubmit(@ModelAttribute MerchantRegisterForm form,
                                 HttpServletRequest request,
                                 Model model) {
        try {
            authClientService.callMerchantRegister(form);
            sessionService.setPendingEmail(request, form.getEmail());
            sessionService.setPendingAction(request, "REGISTER");
            return "redirect:/app/merchant/email-verify";
        } catch (Exception e) {
            log.warn("Registration failed: {}", e.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("error", e.getMessage());
            return "merchant/register";
        }
    }

    // ------------------------------------------------------------------ //
    //  Login
    // ------------------------------------------------------------------ //

    @GetMapping("/login")
    public String loginPage(Model model) {
        return "merchant/login";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam String email,
                              @RequestParam String password,
                              HttpServletRequest request,
                              Model model) {
        try {
            authClientService.callMerchantLogin(email, password);
            sessionService.setPendingEmail(request, email);
            sessionService.setPendingAction(request, "LOGIN");
            return "redirect:/app/merchant/email-verify";
        } catch (Exception e) {
            log.warn("Login failed: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "merchant/login";
        }
    }

    // ------------------------------------------------------------------ //
    //  Email-verify (OTP)
    // ------------------------------------------------------------------ //

    @GetMapping("/email-verify")
    public String emailVerifyPage(HttpServletRequest request, Model model) {
        String pendingEmail  = sessionService.getPendingEmail(request);
        String pendingAction = sessionService.getPendingAction(request);

        // Mask the email for display: show only first 2 chars + domain
        model.addAttribute("maskedEmail", maskEmail(pendingEmail));
        model.addAttribute("email",       pendingEmail);
        model.addAttribute("action",      pendingAction != null ? pendingAction : "LOGIN");
        return "merchant/email-verify";
    }

    @PostMapping("/email-verify/{action}")
    public String emailVerifySubmit(@PathVariable String action,
                                    @RequestParam String email,
                                    @RequestParam String otp,
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    Model model) {
        try {
            AuthVerifyResult result = authClientService.callMerchantEmailVerify(action, email, otp);

            if ("LOGIN".equalsIgnoreCase(action)) {
                // Forward Set-Cookie headers (JWT) to the browser
                if (result.getSetCookieHeaders() != null) {
                    result.getSetCookieHeaders().forEach(
                        cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie)
                    );
                }

                // Use the new access_token to call /auth/me and create session
                String accessToken = result.getAccessToken();
                if (accessToken == null || accessToken.isBlank()) {
                    throw new RuntimeException("No access token received after login");
                }

                MeResponse me = authClientService.callMe(accessToken);
                sessionService.createSession(request, SessionData.builder()
                    .actorId(me.getActorId())
                    .actorType(me.getActorType())
                    .actorName(me.getActorName())
                    .email(me.getEmail())
                    .build());

                sessionService.clearPendingEmail(request);
                return "redirect:/app/merchant/home";

            } else {
                // REGISTER — verification complete, redirect to login with success message
                sessionService.clearPendingEmail(request);
                return "redirect:/app/merchant/login?registered=true";
            }

        } catch (Exception e) {
            log.warn("Email verification failed: {}", e.getMessage());
            model.addAttribute("error",  e.getMessage());
            model.addAttribute("email",  email);
            model.addAttribute("action", action);
            model.addAttribute("maskedEmail", maskEmail(email));
            return "merchant/email-verify";
        }
    }

    // ------------------------------------------------------------------ //
    //  OTP resend
    // ------------------------------------------------------------------ //

    @PostMapping("/otp/resend")
    public String resendOtp(@RequestParam String email,
                            HttpServletRequest request,
                            Model model) {
        try {
            authClientService.callResendOtp(email);
            return "redirect:/app/merchant/email-verify?resent=true";
        } catch (Exception e) {
            log.warn("OTP resend failed: {}", e.getMessage());
            return "redirect:/app/merchant/email-verify?error=" +
                   java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ //
    //  Home
    // ------------------------------------------------------------------ //

    @GetMapping("/home")
    public String homePage(HttpServletRequest request, Model model) {
        SessionData session = sessionService.getSession(request);
        model.addAttribute("actorName", session != null ? session.getActorName() : "");
        model.addAttribute("email",     session != null ? session.getEmail()     : "");
        return "merchant/home";
    }

    // ------------------------------------------------------------------ //
    //  Logout
    // ------------------------------------------------------------------ //

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken  = authClientService.extractCookieValue(request, "access_token");
        String refreshToken = authClientService.extractCookieValue(request, "refresh_token");
        authClientService.callLogout(accessToken, refreshToken, response);
        sessionService.clearSession(request);
        return "redirect:/app/merchant/login";
    }

    // ------------------------------------------------------------------ //
    //  Utilities
    // ------------------------------------------------------------------ //

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@", 2);
        String local  = parts[0];
        String domain = parts[1];
        String masked = local.length() <= 2
            ? local + "***"
            : local.substring(0, 2) + "***";
        return masked + "@" + domain;
    }
}
