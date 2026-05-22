package com.ai.engine.backend.service;

import com.ai.engine.backend.dto.GitHubPushEventDTO;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.normalization.EventNormalizer;
import com.ai.engine.backend.repository.EngineeringEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventIngestionService {

    private final EngineeringEventRepository repository;
    private final ObjectMapper objectMapper;
    private final List<EventNormalizer> normalizers;

    public EventIngestionService(EngineeringEventRepository repository, ObjectMapper objectMapper, List<EventNormalizer> normalizers) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.normalizers = normalizers;
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
}