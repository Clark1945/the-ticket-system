package com.ticketsystem.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(ErrorCode errorCode, HttpStatus httpStatus) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public static BusinessException notFound(ErrorCode code) {
        return new BusinessException(code, HttpStatus.NOT_FOUND);
    }

    public static BusinessException badRequest(ErrorCode code) {
        return new BusinessException(code, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException unauthorized(ErrorCode code) {
        return new BusinessException(code, HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException forbidden(ErrorCode code) {
        return new BusinessException(code, HttpStatus.FORBIDDEN);
    }

    public static BusinessException conflict(ErrorCode code) {
        return new BusinessException(code, HttpStatus.CONFLICT);
    }
}
