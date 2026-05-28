package com.ai.engine.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.dto.EngineerActivityDTO;
import com.ai.engine.backend.repository.EngineeringEventRepository;

@Service
public class AnalyticsService {

    private final EngineeringEventRepository repository;
    private final TenantContext tenantContext;

    public AnalyticsService(
            EngineeringEventRepository repository,
            TenantContext tenantContext
    ) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    /*
     * Engineer activity leaderboard
     */
    public List<EngineerActivityDTO> getEngineerActivity() {

        List<Object[]> rows =
                repository.getEngineerActivityStats(tenantContext.getCurrentTenantId());

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