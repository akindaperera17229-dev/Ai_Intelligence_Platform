package com.ai.engine.backend.repository;

import com.ai.engine.backend.model.Engineer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EngineerRepository extends JpaRepository<Engineer, Long> {
    Optional<Engineer> findByEmail(String email);
}
