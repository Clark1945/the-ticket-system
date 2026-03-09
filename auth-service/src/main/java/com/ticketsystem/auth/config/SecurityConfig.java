package com.ticketsystem.auth.config;

import com.ticketsystem.auth.filter.JwtAuthenticationFilter;
import com.ticketsystem.auth.oauth2.CustomOAuth2UserService;
import com.ticketsystem.auth.oauth2.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService oAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // stateless API, do not use HttpSession, use JWT Token instead
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/merchant/register",
                    "/auth/merchant/login",
                    "/auth/merchant/email-verify/**",
                    "/auth/merchant/otp/resend",
                    "/auth/token/refresh"
                ).permitAll()
                // OAuth2 endpoints are handled by Spring Security's OAuth2 login filter
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(endpoint -> endpoint.baseUri("/auth/oauth2"))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri("/auth/oauth2/*/callback"))
                    .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
            )
            // Add Custom JWT Filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
