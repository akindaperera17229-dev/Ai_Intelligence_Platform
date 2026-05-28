package com.ai.engine.backend.repository;

import com.ai.engine.backend.model.UserPlatformIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPlatformIdentityRepository extends JpaRepository<UserPlatformIdentity, Long> {
    @Query("SELECT u FROM UserPlatformIdentity u WHERE u.tenantId = :tenantId AND u.platform = :platform AND u.platformUserId = :platformUserId")
    Optional<UserPlatformIdentity> findByPlatformAndPlatformUserId(
            @Param("tenantId") UUID tenantId,
            @Param("platform") String platform,
            @Param("platformUserId") String platformUserId
    );
}
