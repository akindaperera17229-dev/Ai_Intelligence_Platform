package com.ai.engine.backend.repository;

import com.ai.engine.backend.model.UserPlatformIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPlatformIdentityRepository extends JpaRepository<UserPlatformIdentity, Long> {
    Optional<UserPlatformIdentity> findByPlatformAndPlatformUserId(String platform, String platformUserId);
}
