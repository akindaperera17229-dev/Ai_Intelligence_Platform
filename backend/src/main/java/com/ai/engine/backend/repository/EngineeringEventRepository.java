package com.ai.engine.backend.repository;

import com.ai.engine.backend.model.EngineeringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EngineeringEventRepository extends JpaRepository<EngineeringEvent, Long> {
    
}
