package com.ticketsystem.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.auth.config.SecurityConfig;
import com.ticketsystem.auth.config.TestSecurityConfig;
import com.ticketsystem.auth.dto.request.EmailVerifyRequest;
import com.ticketsystem.auth.dto.request.MerchantLoginRequest;
import com.ticketsystem.auth.dto.request.MerchantRegisterRequest;
import com.ticketsystem.auth.dto.request.OtpResendRequest;
import com.ticketsystem.auth.exception.BusinessException;
import com.ticketsystem.auth.exception.ErrorCode;
import com.ticketsystem.auth.filter.JwtAuthenticationFilter;
import com.ticketsystem.auth.service.MerchantAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = MerchantAuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class MerchantAuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    MerchantAuthService merchantAuthService;

    // ── POST /auth/merchant/register ──────────────────────────────────────────

    @Test
    void register_success() throws Exception {
        var request = new MerchantRegisterRequest("Test Merchant", "test@example.com",
                "password123", "123 Street", "0912345678");

        mockMvc.perform(post("/auth/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(merchantAuthService).register(any());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        doThrow(BusinessException.conflict(ErrorCode.MERCHANT_ALREADY_EXISTS))
                .when(merchantAuthService).register(any());

        var request = new MerchantRegisterRequest("Test Merchant", "dup@example.com",
                "password123", null, null);

        mockMvc.perform(post("/auth/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.MERCHANT_ALREADY_EXISTS.getMessage()));
    }

    @Test
    void register_missingName_returns400() throws Exception {
        var request = new MerchantRegisterRequest("", "test@example.com",
                "password123", null, null);

        mockMvc.perform(post("/auth/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        var request = new MerchantRegisterRequest("Test", "not-an-email",
                "password123", null, null);

        mockMvc.perform(post("/auth/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        var request = new MerchantRegisterRequest("Test", "test@example.com",
                "short", null, null);

        mockMvc.perform(post("/auth/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST /auth/merchant/login ─────────────────────────────────────────────

    @Test
    void login_success() throws Exception {
        var request = new MerchantLoginRequest("test@example.com", "password123");

        mockMvc.perform(post("/auth/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OTP sent to your email."));

        verify(merchantAuthService).login(any());
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        doThrow(BusinessException.unauthorized(ErrorCode.INVALID_CREDENTIALS))
                .when(merchantAuthService).login(any());

        var request = new MerchantLoginRequest("test@example.com", "wrongpass");

        mockMvc.perform(post("/auth/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.getMessage()));
    }

    @Test
    void login_emailNotVerified_returns403() throws Exception {
        doThrow(BusinessException.forbidden(ErrorCode.EMAIL_NOT_VERIFIED))
                .when(merchantAuthService).login(any());

        var request = new MerchantLoginRequest("test@example.com", "password123");

        mockMvc.perform(post("/auth/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.EMAIL_NOT_VERIFIED.getMessage()));
    }

    @Test
    void login_suspendedMerchant_returns403() throws Exception {
        doThrow(BusinessException.forbidden(ErrorCode.MERCHANT_SUSPENDED))
                .when(merchantAuthService).login(any());

        var request = new MerchantLoginRequest("suspended@example.com", "password123");

        mockMvc.perform(post("/auth/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.MERCHANT_SUSPENDED.getMessage()));
    }

    // ── POST /auth/merchant/email-verify/{action} ─────────────────────────────

    @Test
    void emailVerify_register_success() throws Exception {
        var request = new EmailVerifyRequest("test@example.com", "123456");

        mockMvc.perform(post("/auth/merchant/email-verify/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(merchantAuthService).verifyEmail(eq("register"), any(), any(), any());
    }

    @Test
    void emailVerify_login_success() throws Exception {
        var request = new EmailVerifyRequest("test@example.com", "654321");

        mockMvc.perform(post("/auth/merchant/email-verify/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(merchantAuthService).verifyEmail(eq("login"), any(), any(), any());
    }

    @Test
    void emailVerify_invalidOtp_returns400() throws Exception {
        doThrow(BusinessException.badRequest(ErrorCode.INVALID_OTP))
                .when(merchantAuthService).verifyEmail(any(), any(), any(), any());

        var request = new EmailVerifyRequest("test@example.com", "000000");

        mockMvc.perform(post("/auth/merchant/email-verify/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_OTP.getMessage()));
    }

    @Test
    void emailVerify_merchantNotFound_returns404() throws Exception {
        doThrow(BusinessException.notFound(ErrorCode.MERCHANT_NOT_FOUND))
                .when(merchantAuthService).verifyEmail(any(), any(), any(), any());

        var request = new EmailVerifyRequest("unknown@example.com", "123456");

        mockMvc.perform(post("/auth/merchant/email-verify/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorCode.MERCHANT_NOT_FOUND.getMessage()));
    }

    // ── POST /auth/merchant/otp/resend ────────────────────────────────────────

    @Test
    void resendOtp_success() throws Exception {
        var request = new OtpResendRequest("test@example.com");

        mockMvc.perform(post("/auth/merchant/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OTP resent to your email."));

        verify(merchantAuthService).resendOtp(any());
    }

    @Test
    void resendOtp_noPendingOtp_returns400() throws Exception {
        doThrow(BusinessException.badRequest(ErrorCode.OTP_NOT_FOUND))
                .when(merchantAuthService).resendOtp(any());

        var request = new OtpResendRequest("test@example.com");

        mockMvc.perform(post("/auth/merchant/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.OTP_NOT_FOUND.getMessage()));
    }

    @Test
    void resendOtp_merchantNotFound_returns404() throws Exception {
        doThrow(BusinessException.notFound(ErrorCode.MERCHANT_NOT_FOUND))
                .when(merchantAuthService).resendOtp(any());

        var request = new OtpResendRequest("nobody@example.com");

        mockMvc.perform(post("/auth/merchant/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorCode.MERCHANT_NOT_FOUND.getMessage()));
    }
}
