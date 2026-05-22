package com.ai.engine.backend.normalization.impl;

import com.ai.engine.backend.model.Engineer;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.normalization.EventNormalizer;
import com.ai.engine.backend.service.IdentityResolutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class SlackMessageNormalizer implements EventNormalizer {

    private final ObjectMapper objectMapper;
    private final IdentityResolutionService identityResolutionService;

    public SlackMessageNormalizer(ObjectMapper objectMapper, IdentityResolutionService identityResolutionService) {
        this.objectMapper = objectMapper;
        this.identityResolutionService = identityResolutionService;
    }

    @Override
    public boolean supports(String source, String eventType) {
        return "SLACK".equalsIgnoreCase(source) && "MESSAGE_SENT".equalsIgnoreCase(eventType);
    }

    @Override
    public EngineeringEvent normalize(String rawPayload) {
        EngineeringEvent event = new EngineeringEvent();
        event.setSource("SLACK");
        event.setEventType("MESSAGE_SENT");
        event.setRawPayload(rawPayload);
        event.setIngestionSource("WEBHOOK");
        event.setTimestamp(LocalDateTime.now());

        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            // Slack payloads can be wrapped inside an 'event' object
            JsonNode eventNode = root.has("event") ? root.path("event") : root;
            
            String slackUser = eventNode.path("user").asText("UNKNOWN_USER");
            String text = eventNode.path("text").asText("");
            String channel = eventNode.path("channel").asText("unknown-channel");
            String ts = eventNode.path("ts").asText("");

            event.setUserId(slackUser);
            event.setEngineerName(slackUser); 
            event.setBranchName(channel); 
            
            // Resolve identity
            Engineer engineer = identityResolutionService.resolveEngineer("SLACK", slackUser, slackUser);
            event.setEngineer(engineer);

            // Clean text snippet for summary
            String snippet = text.length() > 60 ? text.substring(0, 57) + "..." : text;
            event.setSummary("Message in #" + channel + ": \"" + snippet + "\"");

            // Extract additional metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("channel", channel);
            metadata.put("slackUserId", slackUser);
            metadata.put("timestamp", ts);
            metadata.put("messageLength", text.length());
            metadata.put("text", text);
            event.setMetadata(metadata);

        } catch (Exception e) {
            event.setSummary("Failed to parse Slack Webhook: " + e.getMessage());
        }

        return event;
    }
}
