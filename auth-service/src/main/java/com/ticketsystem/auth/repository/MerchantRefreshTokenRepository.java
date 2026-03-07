package com.ticketsystem.auth.repository;

import com.ticketsystem.auth.entity.MerchantRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRefreshTokenRepository extends JpaRepository<MerchantRefreshToken, Long> {
    Optional<MerchantRefreshToken> findByRefreshToken(String refreshToken);
    void deleteByMerchantId(Long merchantId);
}
