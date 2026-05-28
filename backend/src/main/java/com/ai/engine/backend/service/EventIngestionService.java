package com.ai.engine.backend.service;

import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.dto.GitHubPushEventDTO;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.normalization.EventNormalizer;
import com.ai.engine.backend.repository.EngineeringEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EventIngestionService {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final EngineeringEventRepository repository;
    private final ObjectMapper objectMapper;
    private final List<EventNormalizer> normalizers;
    private final TenantContext tenantContext;

    public EventIngestionService(
            EngineeringEventRepository repository,
            ObjectMapper objectMapper,
            List<EventNormalizer> normalizers,
            TenantContext tenantContext
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.normalizers = normalizers;
        this.tenantContext = tenantContext;
    }

    /**
     * Generic ingestion pipeline using the strategy pattern normalizers.
     */
    public EngineeringEvent ingestEvent(String source, String eventType, String rawPayload) {
        // Find supporting normalizer
        EventNormalizer normalizer = normalizers.stream()
                .filter(n -> n.supports(source, eventType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No normalizer found for source: " + source + " and eventType: " + eventType));

        EngineeringEvent event = normalizer.normalize(rawPayload);
        event.setTenantId(currentTenantIdOrDefault());
        return repository.save(event);
    }

    /**
     * Legacy method maintained for compatibility.
     */
    public EngineeringEvent processGitHubPush(GitHubPushEventDTO dto) {
        try {
            String rawJson = objectMapper.writeValueAsString(dto);
            return ingestEvent("GITHUB", "CODE_PUSH", rawJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize DTO: " + e.getMessage(), e);
        }
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
