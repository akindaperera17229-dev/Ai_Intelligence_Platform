package com.ai.engine.backend.repository;

import com.ai.engine.backend.model.Engineer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EngineerRepository extends JpaRepository<Engineer, Long> {
    @Query("SELECT e FROM Engineer e WHERE e.tenantId = :tenantId AND e.email = :email")
    Optional<Engineer> findByEmail(@Param("tenantId") UUID tenantId, @Param("email") String email);
}
