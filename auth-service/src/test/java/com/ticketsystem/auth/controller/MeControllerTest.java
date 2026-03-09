package com.ticketsystem.auth.controller;

import com.ticketsystem.auth.config.SecurityConfig;
import com.ticketsystem.auth.config.TestSecurityConfig;
import com.ticketsystem.auth.entity.AppUser;
import com.ticketsystem.auth.entity.Merchant;
import com.ticketsystem.auth.exception.ErrorCode;
import com.ticketsystem.auth.filter.JwtAuthenticationFilter;
import com.ticketsystem.auth.repository.AppUserRepository;
import com.ticketsystem.auth.repository.MerchantRepository;
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
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = MeController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class MeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MerchantRepository merchantRepository;

    @MockBean
    AppUserRepository userRepository;

    // ── GET /auth/me ──────────────────────────────────────────────────────────

    @Test
    void me_asMerchant_success() throws Exception {
        Merchant merchant = new Merchant();
        merchant.setName("Test Merchant");
        merchant.setEmail("merchant@example.com");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        var auth = new UsernamePasswordAuthenticationToken("1:MERCHANT", null, Collections.emptyList());

        mockMvc.perform(get("/auth/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actorId").value(1))
                .andExpect(jsonPath("$.data.actorType").value("MERCHANT"))
                .andExpect(jsonPath("$.data.actorName").value("Test Merchant"))
                .andExpect(jsonPath("$.data.email").value("merchant@example.com"));

        verify(merchantRepository).findById(1L);
        verifyNoInteractions(userRepository);
    }

    @Test
    void me_asUser_success() throws Exception {
        AppUser user = new AppUser();
        user.setName("Test User");
        user.setEmail("user@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        var auth = new UsernamePasswordAuthenticationToken("2:USER", null, Collections.emptyList());

        mockMvc.perform(get("/auth/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actorId").value(2))
                .andExpect(jsonPath("$.data.actorType").value("USER"))
                .andExpect(jsonPath("$.data.actorName").value("Test User"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));

        verify(userRepository).findById(2L);
        verifyNoInteractions(merchantRepository);
    }

    @Test
    void me_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(merchantRepository, userRepository);
    }

    @Test
    void me_merchantNotFound_returns404() throws Exception {
        when(merchantRepository.findById(99L)).thenReturn(Optional.empty());

        var auth = new UsernamePasswordAuthenticationToken("99:MERCHANT", null, Collections.emptyList());

        mockMvc.perform(get("/auth/me").with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.MERCHANT_NOT_FOUND.getMessage()));
    }

    @Test
    void me_userNotFound_returns404() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        var auth = new UsernamePasswordAuthenticationToken("99:USER", null, Collections.emptyList());

        mockMvc.perform(get("/auth/me").with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
    }
}
