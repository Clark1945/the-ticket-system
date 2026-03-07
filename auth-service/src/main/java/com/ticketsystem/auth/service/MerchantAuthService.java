package com.ticketsystem.auth.service;

import com.ticketsystem.auth.dto.request.EmailVerifyRequest;
import com.ticketsystem.auth.dto.request.MerchantLoginRequest;
import com.ticketsystem.auth.dto.request.MerchantRegisterRequest;
import com.ticketsystem.auth.dto.request.OtpResendRequest;
import com.ticketsystem.auth.entity.Merchant;
import com.ticketsystem.auth.entity.MerchantRefreshToken;
import com.ticketsystem.auth.enums.ActorType;
import com.ticketsystem.auth.enums.AuditAction;
import com.ticketsystem.auth.enums.MerchantStatus;
import com.ticketsystem.auth.exception.BusinessException;
import com.ticketsystem.auth.exception.ErrorCode;
import com.ticketsystem.auth.repository.MerchantRefreshTokenRepository;
import com.ticketsystem.auth.repository.MerchantRepository;
import com.ticketsystem.auth.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class MerchantAuthService {

    private final MerchantRepository merchantRepository;
    private final MerchantRefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    public void register(MerchantRegisterRequest request) {
        if (merchantRepository.existsByEmail(request.email())) {
            throw BusinessException.conflict(ErrorCode.MERCHANT_ALREADY_EXISTS);
        }

        Merchant merchant = new Merchant();
        merchant.setName(request.name());
        merchant.setEmail(request.email());
        merchant.setPasswordHash(passwordEncoder.encode(request.password()));
        merchant.setAddress(request.address());
        merchant.setPhone(request.phone());
        merchantRepository.save(merchant);

        String otp = otpService.generateAndSave(merchant.getId(), "REGISTER");
        emailService.sendOtp(merchant.getEmail(), otp, "Registration");
    }

    public void login(MerchantLoginRequest request) {
        Merchant merchant = merchantRepository.findByEmail(request.email())
                .orElseThrow(() -> BusinessException.unauthorized(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), merchant.getPasswordHash())) {
            throw BusinessException.unauthorized(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!merchant.isEmailVerified()) {
            throw BusinessException.forbidden(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (merchant.getStatus() == MerchantStatus.SUSPENDED) {
            throw BusinessException.forbidden(ErrorCode.MERCHANT_SUSPENDED);
        }

        String otp = otpService.generateAndSave(merchant.getId(), "LOGIN");
        emailService.sendOtp(merchant.getEmail(), otp, "Login");
    }

    public void verifyEmail(String action, EmailVerifyRequest request,
                            HttpServletRequest httpRequest, HttpServletResponse response) {
        String normalizedAction = action.toUpperCase();
        if (!normalizedAction.equals("REGISTER") && !normalizedAction.equals("LOGIN")) {
            throw BusinessException.badRequest(ErrorCode.INVALID_OTP);
        }

        Merchant merchant = merchantRepository.findByEmail(request.email())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.MERCHANT_NOT_FOUND));

        if (!otpService.validate(merchant.getId(), normalizedAction, request.otp())) {
            throw BusinessException.badRequest(ErrorCode.INVALID_OTP);
        }
        otpService.delete(merchant.getId(), normalizedAction);

        if (normalizedAction.equals("REGISTER")) {
            merchant.setEmailVerified(true);
            merchantRepository.save(merchant);
        } else {
            issueTokens(merchant, response);
            auditLogService.log(merchant.getId(), ActorType.MERCHANT, AuditAction.LOGIN,
                    httpRequest.getRemoteAddr(), null);
        }
    }

    public void resendOtp(OtpResendRequest request) {
        Merchant merchant = merchantRepository.findByEmail(request.email())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.MERCHANT_NOT_FOUND));

        // Determine which OTP type is pending and resend it
        boolean registerPending = otpService.exists(merchant.getId(), "REGISTER");
        boolean loginPending = otpService.exists(merchant.getId(), "LOGIN");

        if (!registerPending && !loginPending) {
            throw BusinessException.badRequest(ErrorCode.OTP_NOT_FOUND);
        }

        if (registerPending) {
            String otp = otpService.generateAndSave(merchant.getId(), "REGISTER");
            emailService.sendOtp(merchant.getEmail(), otp, "Registration");
        }
        if (loginPending) {
            String otp = otpService.generateAndSave(merchant.getId(), "LOGIN");
            emailService.sendOtp(merchant.getEmail(), otp, "Login");
        }
    }

    private void issueTokens(Merchant merchant, HttpServletResponse response) {
        String accessToken = jwtService.generateAccessToken(merchant.getId(), ActorType.MERCHANT);
        String refreshToken = jwtService.generateRefreshToken(merchant.getId(), ActorType.MERCHANT);

        MerchantRefreshToken token = new MerchantRefreshToken();
        token.setMerchant(merchant);
        token.setRefreshToken(refreshToken);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry));
        refreshTokenRepository.save(token);

        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.createAccessTokenCookie(accessToken, jwtService.getAccessTokenExpiry()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.createRefreshTokenCookie(refreshToken, refreshTokenExpiry).toString());
    }
}
