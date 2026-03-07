package com.ticketsystem.auth.controller;

import com.ticketsystem.auth.dto.request.EmailVerifyRequest;
import com.ticketsystem.auth.dto.request.MerchantLoginRequest;
import com.ticketsystem.auth.dto.request.MerchantRegisterRequest;
import com.ticketsystem.auth.dto.request.OtpResendRequest;
import com.ticketsystem.auth.dto.response.ApiResponse;
import com.ticketsystem.auth.service.MerchantAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/merchant")
@RequiredArgsConstructor
public class MerchantAuthController {

    private final MerchantAuthService merchantAuthService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody MerchantRegisterRequest request) {
        merchantAuthService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Registration successful. Please check your email for the OTP."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(
            @Valid @RequestBody MerchantLoginRequest request) {
        merchantAuthService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("OTP sent to your email."));
    }

    @PostMapping("/email-verify/{action}")
    public ResponseEntity<ApiResponse<Void>> emailVerify(
            @PathVariable String action,
            @Valid @RequestBody EmailVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        merchantAuthService.verifyEmail(action, request, httpRequest, response);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/otp/resend")
    public ResponseEntity<ApiResponse<Void>> resendOtp(
            @Valid @RequestBody OtpResendRequest request) {
        merchantAuthService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("OTP resent to your email."));
    }
}
