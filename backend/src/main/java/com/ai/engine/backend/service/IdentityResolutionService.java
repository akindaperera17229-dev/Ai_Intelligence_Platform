package com.ai.engine.backend.service;

import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.model.Engineer;
import com.ai.engine.backend.model.UserPlatformIdentity;
import com.ai.engine.backend.repository.EngineerRepository;
import com.ai.engine.backend.repository.UserPlatformIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class IdentityResolutionService {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final EngineerRepository engineerRepository;
    private final UserPlatformIdentityRepository identityRepository;
    private final TenantContext tenantContext;

    public IdentityResolutionService(
            EngineerRepository engineerRepository,
            UserPlatformIdentityRepository identityRepository,
            TenantContext tenantContext
    ) {
        this.engineerRepository = engineerRepository;
        this.identityRepository = identityRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public Engineer resolveEngineer(String platform, String platformUserId, String defaultName) {
        if (platformUserId == null || platformUserId.trim().isEmpty()) {
            return null;
        }

        UUID tenantId = currentTenantIdOrDefault();

        // 1. Check if the platform identity is already mapped
        Optional<UserPlatformIdentity> existingIdentity = identityRepository
                .findByPlatformAndPlatformUserId(tenantId, platform.toUpperCase(), platformUserId);

        if (existingIdentity.isPresent()) {
            return existingIdentity.get().getEngineer();
        }

        // 2. Not mapped. Create a new Engineer profile (Auto-creation fallback)
        String displayName = (defaultName != null && !defaultName.trim().isEmpty()) ? defaultName : platformUserId;
        
        Engineer newEngineer = Engineer.builder()
                .tenantId(tenantId)
                .fullName(displayName)
                .email(platformUserId.contains("@") ? platformUserId : null)
                .build();

        newEngineer = engineerRepository.save(newEngineer);

        // 3. Register this platform identity mapping
        UserPlatformIdentity newIdentity = UserPlatformIdentity.builder()
                .tenantId(tenantId)
                .platform(platform.toUpperCase())
                .platformUserId(platformUserId)
                .engineer(newEngineer)
                .build();

        identityRepository.save(newIdentity);

        return newEngineer;
    }

    private UUID currentTenantIdOrDefault() {
        try {
            if (tenantContext.hasTenant()) {
                return tenantContext.getCurrentTenantId();
            }
        } catch (RuntimeException ignored) {
            // Scheduled jobs and legacy/static webhooks may run outside an HTTP request.
        }
        return DEFAULT_TENANT_ID;
    }
}
