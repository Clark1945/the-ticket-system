package com.ticketsystem.frontend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthVerifyResult {
    /**
     * The raw Set-Cookie header values returned by the auth service.
     * Forwarded to the browser response as-is.
     */
    private List<String> setCookieHeaders;

    /**
     * The extracted access_token value parsed from the Set-Cookie headers.
     * Used to call /auth/me immediately after successful login.
     */
    private String accessToken;
}
