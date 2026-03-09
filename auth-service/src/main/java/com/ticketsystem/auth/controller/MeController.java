package com.ticketsystem.auth.controller;

import com.ticketsystem.auth.dto.response.ApiResponse;
import com.ticketsystem.auth.dto.response.MeResponse;
import com.ticketsystem.auth.entity.AppUser;
import com.ticketsystem.auth.entity.Merchant;
import com.ticketsystem.auth.enums.ActorType;
import com.ticketsystem.auth.exception.BusinessException;
import com.ticketsystem.auth.exception.ErrorCode;
import com.ticketsystem.auth.repository.AppUserRepository;
import com.ticketsystem.auth.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class MeController {

    private final MerchantRepository merchantRepository;
    private final AppUserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw BusinessException.unauthorized(ErrorCode.TOKEN_INVALID);
        }

        // Principal is set by JwtAuthenticationFilter as "{actorId}:{actorType}"
        String principal = (String) authentication.getPrincipal();
        String[] parts = principal.split(":", 2);
        Long actorId = Long.parseLong(parts[0]);
        String actorType = parts[1];

        if (ActorType.MERCHANT.name().equals(actorType)) {
            Merchant merchant = merchantRepository.findById(actorId)
                    .orElseThrow(() -> BusinessException.notFound(ErrorCode.MERCHANT_NOT_FOUND));
            return ResponseEntity.ok(ApiResponse.ok(
                    new MeResponse(actorId, actorType, merchant.getName(), merchant.getEmail())
            ));
        } else {
            AppUser user = userRepository.findById(actorId)
                    .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND));
            return ResponseEntity.ok(ApiResponse.ok(
                    new MeResponse(actorId, actorType, user.getName(), user.getEmail())
            ));
        }
    }
}
