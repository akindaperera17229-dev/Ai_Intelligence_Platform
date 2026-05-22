package com.ai.engine.backend.service;

import com.ai.engine.backend.model.Engineer;
import com.ai.engine.backend.model.UserPlatformIdentity;
import com.ai.engine.backend.repository.EngineerRepository;
import com.ai.engine.backend.repository.UserPlatformIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IdentityResolutionService {

    private final EngineerRepository engineerRepository;
    private final UserPlatformIdentityRepository identityRepository;

    public IdentityResolutionService(EngineerRepository engineerRepository, UserPlatformIdentityRepository identityRepository) {
        this.engineerRepository = engineerRepository;
        this.identityRepository = identityRepository;
    }

    @Transactional
    public Engineer resolveEngineer(String platform, String platformUserId, String defaultName) {
        if (platformUserId == null || platformUserId.trim().isEmpty()) {
            return null;
        }

        // 1. Check if the platform identity is already mapped
        Optional<UserPlatformIdentity> existingIdentity = identityRepository
                .findByPlatformAndPlatformUserId(platform.toUpperCase(), platformUserId);

        if (existingIdentity.isPresent()) {
            return existingIdentity.get().getEngineer();
        }

        // 2. Not mapped. Create a new Engineer profile (Auto-creation fallback)
        String displayName = (defaultName != null && !defaultName.trim().isEmpty()) ? defaultName : platformUserId;
        
        Engineer newEngineer = Engineer.builder()
                .fullName(displayName)
                .email(platformUserId.contains("@") ? platformUserId : null)
                .build();

        newEngineer = engineerRepository.save(newEngineer);

        // 3. Register this platform identity mapping
        UserPlatformIdentity newIdentity = UserPlatformIdentity.builder()
                .platform(platform.toUpperCase())
                .platformUserId(platformUserId)
                .engineer(newEngineer)
                .build();

        identityRepository.save(newIdentity);

        return newEngineer;
    }
}
