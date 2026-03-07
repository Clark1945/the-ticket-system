package com.ticketsystem.auth.repository;

import com.ticketsystem.auth.entity.UserAuthProvider;
import com.ticketsystem.auth.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, Long> {
    Optional<UserAuthProvider> findByProviderAndProviderUid(OAuthProvider provider, String providerUid);
}
