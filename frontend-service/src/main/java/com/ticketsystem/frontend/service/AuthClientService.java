package com.ticketsystem.frontend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.frontend.dto.ApiResponse;
import com.ticketsystem.frontend.dto.AuthVerifyResult;
import com.ticketsystem.frontend.dto.MeResponse;
import com.ticketsystem.frontend.dto.MerchantRegisterForm;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthClientService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ //
    //  /auth/me
    // ------------------------------------------------------------------ //

    /**
     * Calls GET /auth/me with the given access_token value in the Cookie header.
     */
    public MeResponse callMe(String accessToken) {
        try {
            ApiResponse<MeResponse> response = webClient.get()
                .uri("/auth/me")
                .header(HttpHeaders.COOKIE, "access_token=" + accessToken)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> buildErrorMono(clientResponse)
                )
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<MeResponse>>() {})
                .block();

            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new RuntimeException("Failed to fetch user info from auth service");
            }
            return response.getData();
        } catch (RuntimeException e) {
            log.error("callMe failed: {}", e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ //
    //  /auth/merchant/register
    // ------------------------------------------------------------------ //

    public void callMerchantRegister(MerchantRegisterForm form) {
        Map<String, String> body = new HashMap<>();
        body.put("name",     form.getName());
        body.put("email",    form.getEmail());
        body.put("password", form.getPassword());
        if (form.getAddress() != null && !form.getAddress().isBlank()) {
            body.put("address", form.getAddress());
        }
        if (form.getPhone() != null && !form.getPhone().isBlank()) {
            body.put("phone", form.getPhone());
        }

        try {
            ApiResponse<?> response = webClient.post()
                .uri("/auth/merchant/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> buildErrorMono(clientResponse)
                )
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                .block();

            if (response != null && !response.isSuccess()) {
                throw new RuntimeException(response.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("callMerchantRegister failed: {}", e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ //
    //  /auth/merchant/login
    // ------------------------------------------------------------------ //

    public void callMerchantLogin(String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("email",    email);
        body.put("password", password);

        try {
            ApiResponse<?> response = webClient.post()
                .uri("/auth/merchant/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> buildErrorMono(clientResponse)
                )
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                .block();

            if (response != null && !response.isSuccess()) {
                throw new RuntimeException(response.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("callMerchantLogin failed: {}", e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ //
    //  /auth/merchant/email-verify/{action}
    // ------------------------------------------------------------------ //

    /**
     * Calls POST /auth/merchant/email-verify/{action}.
     * Returns an AuthVerifyResult that contains the raw Set-Cookie headers
     * and the extracted access_token value (relevant for LOGIN action).
     */
    public AuthVerifyResult callMerchantEmailVerify(String action, String email, String otp) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("otp",   otp);

        try {
            // Use exchangeToMono to access the full ClientResponse (headers + body)
            ClientResponse clientResponse = webClient.post()
                .uri("/auth/merchant/email-verify/{action}", action)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(cr -> {
                    if (cr.statusCode().is4xxClientError() || cr.statusCode().is5xxServerError()) {
                        return cr.bodyToMono(String.class).flatMap(errorBody -> {
                            String errorMsg = extractMessageFromBody(errorBody);
                            return Mono.error(new RuntimeException(errorMsg));
                        });
                    }
                    return Mono.just(cr);
                })
                .block();

            if (clientResponse == null) {
                throw new RuntimeException("No response from auth service");
            }

            List<String> setCookieHeaders = clientResponse.headers()
                .header(HttpHeaders.SET_COOKIE);

            String accessToken = extractAccessTokenFromSetCookie(setCookieHeaders);

            // Consume the body to release the connection
            clientResponse.releaseBody().block();

            return new AuthVerifyResult(setCookieHeaders, accessToken);

        } catch (RuntimeException e) {
            log.error("callMerchantEmailVerify failed: {}", e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ //
    //  /auth/merchant/otp/resend
    // ------------------------------------------------------------------ //

    public void callResendOtp(String email) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);

        try {
            ApiResponse<?> response = webClient.post()
                .uri("/auth/merchant/otp/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> buildErrorMono(clientResponse)
                )
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                .block();

            if (response != null && !response.isSuccess()) {
                throw new RuntimeException(response.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("callResendOtp failed: {}", e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ //
    //  /auth/logout
    // ------------------------------------------------------------------ //

    /**
     * Calls POST /auth/logout, forwarding JWT cookies so the auth service can
     * blacklist the tokens.
     */
    public void callLogout(String accessToken, String refreshToken, HttpServletResponse response) {
        String cookieHeader = buildCookieHeader(accessToken, refreshToken);

        try {
            webClient.post()
                .uri("/auth/logout")
                .header(HttpHeaders.COOKIE, cookieHeader)
                .retrieve()
                .onStatus(
                    status -> status.is5xxServerError(),
                    clientResponse -> buildErrorMono(clientResponse)
                )
                .bodyToMono(Void.class)
                .block();
        } catch (Exception e) {
            // Log but do not re-throw — session will be cleared regardless
            log.warn("callLogout failed (session will still be cleared): {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  /auth/token/refresh
    // ------------------------------------------------------------------ //

    /**
     * Attempts to refresh the access_token using the refresh_token cookie.
     *
     * @return the new access_token value on success, or {@code null} on failure.
     */
    public String callRefreshToken(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        try {
            ClientResponse clientResponse = webClient.post()
                .uri("/auth/token/refresh")
                .header(HttpHeaders.COOKIE, "refresh_token=" + refreshToken)
                .exchangeToMono(cr -> Mono.just(cr))
                .block();

            if (clientResponse == null || !clientResponse.statusCode().is2xxSuccessful()) {
                if (clientResponse != null) clientResponse.releaseBody().block();
                return null;
            }

            // Forward new Set-Cookie headers to browser
            List<String> setCookieHeaders = clientResponse.headers().header(HttpHeaders.SET_COOKIE);
            setCookieHeaders.forEach(cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie));

            // Extract new access_token value
            String newAccessToken = extractAccessTokenFromSetCookie(setCookieHeaders);

            clientResponse.releaseBody().block();
            return newAccessToken;

        } catch (Exception e) {
            log.warn("callRefreshToken failed: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    /**
     * Extracts the access_token value from a list of Set-Cookie header strings.
     * A typical header looks like:
     *   access_token=eyJ...; Path=/; HttpOnly; SameSite=Strict
     */
    public String extractAccessTokenFromSetCookie(List<String> setCookieHeaders) {
        if (setCookieHeaders == null) return null;
        for (String header : setCookieHeaders) {
            if (header.startsWith("access_token=")) {
                String[] parts = header.split(";");
                String tokenPart = parts[0].trim(); // "access_token=eyJ..."
                return tokenPart.substring("access_token=".length());
            }
        }
        return null;
    }

    /**
     * Extracts cookies named access_token and refresh_token from the incoming
     * browser request for forwarding to auth service.
     */
    public String extractCookieValue(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(c -> cookieName.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private String buildCookieHeader(String accessToken, String refreshToken) {
        StringBuilder sb = new StringBuilder();
        if (accessToken != null && !accessToken.isBlank()) {
            sb.append("access_token=").append(accessToken);
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("refresh_token=").append(refreshToken);
        }
        return sb.toString();
    }

    /**
     * Builds an error Mono by reading the response body and extracting the
     * message field from the auth service's ApiResponse envelope.
     */
    private Mono<Throwable> buildErrorMono(ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
            .defaultIfEmpty("{}")
            .map(body -> {
                String message = extractMessageFromBody(body);
                return (Throwable) new RuntimeException(message);
            });
    }

    private String extractMessageFromBody(String body) {
        try {
            ApiResponse<?> apiResponse = objectMapper.readValue(body,
                new TypeReference<ApiResponse<Void>>() {});
            if (apiResponse.getMessage() != null && !apiResponse.getMessage().isBlank()) {
                return apiResponse.getMessage();
            }
        } catch (Exception ignored) {
            // Body is not an ApiResponse — use raw body
        }
        return body.isBlank() ? "Unexpected error from auth service" : body;
    }
}
