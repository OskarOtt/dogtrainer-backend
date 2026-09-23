package com.oskott.dogtrainerbackend.auth.repository;

import com.oskott.dogtrainerbackend.auth.entity.ExternalAuthProvider;
import com.oskott.dogtrainerbackend.auth.entity.ExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findByProviderAndProviderSubject(
            ExternalAuthProvider provider,
            String providerSubject
    );

    List<ExternalIdentity> findAllByUserId(UUID userId);

    boolean existsByUserIdAndProvider(UUID userId, ExternalAuthProvider provider);

    void deleteByUserId(UUID userId);
}
