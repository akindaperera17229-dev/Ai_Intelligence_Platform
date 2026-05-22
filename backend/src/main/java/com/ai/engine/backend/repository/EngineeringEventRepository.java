package com.ai.engine.backend.repository;

import com.ai.engine.backend.model.EngineeringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EngineeringEventRepository extends JpaRepository<EngineeringEvent, Long> {
    
    @Query("""
            SELECT e.engineerName, COUNT(e)
            FROM EngineeringEvent e
            GROUP BY e.engineerName
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> getEngineerActivityStats();

    @Query("""
            SELECT e.repositoryName, COUNT(e)
            FROM EngineeringEvent e
            GROUP BY e.repositoryName
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> getRepositoryStats();
}
