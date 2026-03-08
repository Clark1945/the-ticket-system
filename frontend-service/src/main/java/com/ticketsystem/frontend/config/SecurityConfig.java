package com.ticketsystem.frontend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Use the default CsrfTokenRequestAttributeHandler so Thymeleaf can pick up the token
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http
            // Allow all requests — page guard is handled by PageGuardInterceptor
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            // Enable CSRF with session-based token (works with Spring Session)
            .csrf(csrf -> csrf
                .csrfTokenRequestHandler(requestHandler)
            )
            // Disable Spring Security's own session management; we use Spring Session via Redis
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.NEVER
                )
            )
            // Disable default login/logout pages — our controllers handle this
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}
