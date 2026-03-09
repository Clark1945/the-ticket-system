package com.ticketsystem.auth.controller;

import com.ticketsystem.auth.config.SecurityConfig;
import com.ticketsystem.auth.config.TestSecurityConfig;
import com.ticketsystem.auth.exception.BusinessException;
import com.ticketsystem.auth.exception.ErrorCode;
import com.ticketsystem.auth.filter.JwtAuthenticationFilter;
import com.ticketsystem.auth.service.CommonAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = CommonAuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class CommonAuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CommonAuthService commonAuthService;

    // ── POST /auth/logout ─────────────────────────────────────────────────────

    @Test
    void logout_success() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken("1:MERCHANT", null, Collections.emptyList());

        mockMvc.perform(post("/auth/logout").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        verify(commonAuthService).logout(any(), any());
    }

    @Test
    void logout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(commonAuthService);
    }

    // ── POST /auth/token/refresh ──────────────────────────────────────────────

    @Test
    void refreshToken_success() throws Exception {
        mockMvc.perform(post("/auth/token/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token refreshed."));

        verify(commonAuthService).refreshToken(any(), any());
    }

    @Test
    void refreshToken_invalidToken_returns401() throws Exception {
        doThrow(BusinessException.unauthorized(ErrorCode.TOKEN_INVALID))
                .when(commonAuthService).refreshToken(any(), any());

        mockMvc.perform(post("/auth/token/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.TOKEN_INVALID.getMessage()));
    }
}
