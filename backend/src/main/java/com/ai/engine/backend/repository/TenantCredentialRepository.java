package com.ai.engine.backend.repository;

import com.ai.engine.backend.model.TenantCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantCredentialRepository extends JpaRepository<TenantCredential, UUID> {
    Optional<TenantCredential> findByTenantIdAndPlatformAndCredentialKey(
            UUID tenantId, String platform, String credentialKey);

    List<TenantCredential> findByTenantIdAndPlatform(UUID tenantId, String platform);
}
