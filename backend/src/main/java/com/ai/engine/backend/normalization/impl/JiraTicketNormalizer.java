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
public class JiraTicketNormalizer implements EventNormalizer {

    private final ObjectMapper objectMapper;
    private final IdentityResolutionService identityResolutionService;

    public JiraTicketNormalizer(ObjectMapper objectMapper, IdentityResolutionService identityResolutionService) {
        this.objectMapper = objectMapper;
        this.identityResolutionService = identityResolutionService;
    }

    @Override
    public boolean supports(String source, String eventType) {
        return "JIRA".equalsIgnoreCase(source) && 
               ("TICKET_UPDATED".equalsIgnoreCase(eventType) || "TICKET_CREATED".equalsIgnoreCase(eventType));
    }

    @Override
    public EngineeringEvent normalize(String rawPayload) {
        EngineeringEvent event = new EngineeringEvent();
        event.setSource("JIRA");
        event.setRawPayload(rawPayload);
        event.setIngestionSource("WEBHOOK");
        event.setTimestamp(LocalDateTime.now());

        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            // Determine event type
            String webhookEvent = root.path("webhookEvent").asText("jira:issue_updated");
            String eventType = webhookEvent.contains("created") ? "TICKET_CREATED" : "TICKET_UPDATED";
            event.setEventType(eventType);

            // Navigate issue details
            JsonNode issueNode = root.path("issue");
            String ticketKey = issueNode.path("key").asText("UNKNOWN-KEY");
            JsonNode fields = issueNode.path("fields");
            String ticketSummary = fields.path("summary").asText("No summary provided");
            String project = fields.path("project").path("name").asText("Unknown Project");
            String status = fields.path("status").path("name").asText("Open");
            String priority = fields.path("priority").path("name").asText("Medium");

            event.setRepositoryName(project); // Map project to repository
            event.setBranchName(ticketKey);   // Map issue key to branch/ref identifier

            // Extract engineer details from assignee, fallback to creator
            JsonNode assigneeNode = fields.path("assignee");
            if (assigneeNode.isMissingNode() || assigneeNode.isNull()) {
                assigneeNode = fields.path("creator");
            }
            
            String displayName = assigneeNode.path("displayName").asText("Unassigned");
            String platformUserId = assigneeNode.path("accountId").asText(displayName);
            event.setEngineerName(displayName);
            event.setUserId(platformUserId);

            // Resolve identity
            Engineer engineer = identityResolutionService.resolveEngineer("JIRA", platformUserId, displayName);
            event.setEngineer(engineer);

            // Build short summary
            String summary = displayName + " updated ticket [" + ticketKey + "] (" + status + ") in project " + project;
            if ("TICKET_CREATED".equals(eventType)) {
                summary = displayName + " created ticket [" + ticketKey + "]: " + ticketSummary;
            }
            event.setSummary(summary);

            // Extract additional metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("ticketKey", ticketKey);
            metadata.put("title", ticketSummary);
            metadata.put("status", status);
            metadata.put("priority", priority);
            metadata.put("project", project);
            double storyPoints = 0.0;
            boolean hasStoryPoints = false;
            if (fields.has("storyPoints")) {
                storyPoints = fields.path("storyPoints").asDouble();
                hasStoryPoints = true;
            } else if (fields.has("story_points")) {
                storyPoints = fields.path("story_points").asDouble();
                hasStoryPoints = true;
            } else if (fields.has("customfield_10016")) {
                storyPoints = fields.path("customfield_10016").asDouble();
                hasStoryPoints = true;
            }
            if (hasStoryPoints) {
                metadata.put("storyPoints", storyPoints);
            }
            event.setMetadata(metadata);

        } catch (Exception e) {
            event.setEventType("TICKET_UPDATED");
            event.setSummary("Failed to parse Jira Webhook: " + e.getMessage());
        }

        return event;
    }
}
