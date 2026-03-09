package com.ticketsystem.auth.controller;

import com.ticketsystem.auth.dto.response.ApiResponse;
import com.ticketsystem.auth.service.CommonAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class CommonAuthController {

    private final CommonAuthService commonAuthService;

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        commonAuthService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully."));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<Void>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        commonAuthService.refreshToken(request, response);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed."));
    }
}
