package com.ticketsystem.auth.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    MERCHANT_NOT_FOUND("Merchant not found"),
    MERCHANT_ALREADY_EXISTS("Merchant with this email already exists"),
    INVALID_CREDENTIALS("Invalid email or password"),
    EMAIL_NOT_VERIFIED("Email is not verified. Please check your inbox."),
    MERCHANT_SUSPENDED("Merchant account is suspended"),
    INVALID_OTP("Invalid or expired OTP"),
    OTP_NOT_FOUND("No pending OTP found for this account"),
    USER_NOT_FOUND("User not found"),
    USER_BANNED("User account is banned"),
    TOKEN_INVALID("Invalid or expired token"),
    TOKEN_BLACKLISTED("Token has been revoked");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }
}
