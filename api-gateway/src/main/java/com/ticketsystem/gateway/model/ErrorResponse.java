package com.ticketsystem.gateway.model;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public record ErrorResponse(String code, String message, String timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(
                code,
                message,
                ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
        );
    }
}
