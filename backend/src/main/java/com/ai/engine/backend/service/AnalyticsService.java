package com.ai.engine.backend.service;

import com.ai.engine.backend.dto.EngineerActivityDTO;
import com.ai.engine.backend.repository.EngineeringEventRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnalyticsService {

    private final EngineeringEventRepository repository;

    public AnalyticsService(
            EngineeringEventRepository repository
    ) {
        this.repository = repository;
    }

    /*
     * Engineer activity leaderboard
     */
    public List<EngineerActivityDTO> getEngineerActivity() {

        List<Object[]> rows =
                repository.getEngineerActivityStats();

        List<EngineerActivityDTO> response =
                new ArrayList<>();

        for (Object[] row : rows) {

            String engineerName =
                    (String) row[0];

            Long totalEvents =
                    (Long) row[1];

            response.add(
                    new EngineerActivityDTO(
                            engineerName,
                            totalEvents
                    )
            );
        }

        return response;
    }
}