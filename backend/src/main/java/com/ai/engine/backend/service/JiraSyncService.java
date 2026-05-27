package com.ai.engine.backend.service;

import com.ai.engine.backend.client.JiraApiClient;
import com.ai.engine.backend.config.JiraConnectionConfig;
import com.ai.engine.backend.dto.jira.JiraIssueDTO;
import com.ai.engine.backend.dto.jira.JiraProjectDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates pulling data from Jira Cloud REST API and feeding it into
 * the existing normalization pipeline (EventIngestionService → JiraTicketNormalizer).
 *
 * Two ingestion modes:
 *   1. Passive (real-time) — Jira sends webhooks → /webhooks/jira → JiraTicketNormalizer  [ALREADY WORKING]
 *   2. Active (scheduled)  — THIS SERVICE pulls all issues nightly and on-demand              [NEW]
 *
 * The active sync ensures we have a full historical baseline even if webhooks
 * were missed or the workspace was connected after events already happened.
 */
@Service
public class JiraSyncService {

    private static final Logger log = LoggerFactory.getLogger(JiraSyncService.class);

    private final JiraApiClient jiraApiClient;
    private final JiraConnectionConfig config;
    private final EventIngestionService eventIngestionService;
    private final ObjectMapper objectMapper;

    public JiraSyncService(JiraApiClient jiraApiClient,
                           JiraConnectionConfig config,
                           EventIngestionService eventIngestionService,
                           ObjectMapper objectMapper) {
        this.jiraApiClient = jiraApiClient;
        this.config = config;
        this.eventIngestionService = eventIngestionService;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------
    // Scheduled full sync — nightly at 2 AM by default
    // Configurable via jira.sync.cron env var
    // -------------------------------------------------------

    @Scheduled(cron = "${jira.sync.cron:0 0 2 * * *}")
    public void scheduledSync() {
        if (!config.isConfigured()) {
            log.debug("Jira sync skipped — not configured. Set JIRA_BASE_URL, JIRA_USER_EMAIL, JIRA_API_TOKEN.");
            return;
        }
        log.info("=== Starting scheduled Jira sync ===");
        SyncResult result = syncAllProjects();
        log.info("=== Scheduled Jira sync complete: {} issues synced, {} failed ===",
                result.synced(), result.failed());
    }

    // -------------------------------------------------------
    // Public API — can be triggered manually via REST controller
    // -------------------------------------------------------

    /**
     * Performs a full sync of all configured Jira projects.
     * If jira.sync.projects is set, only those project keys are synced.
     * Otherwise, all accessible projects are synced.
     *
     * @return SyncResult with counts of synced and failed issues
     */
    public SyncResult syncAllProjects() {
        if (!config.hasCredentials()) {
            log.warn("Jira sync aborted — credentials not configured.");
            return new SyncResult(0, 0, "Credentials not configured");
        }

        int totalSynced = 0;
        int totalFailed = 0;

        try {
            List<String> projectKeys = config.getSync().getProjectKeyList();

            if (projectKeys.isEmpty()) {
                // Sync all accessible projects
                List<JiraProjectDTO> projects = jiraApiClient.getProjects();
                log.info("Syncing all {} accessible Jira projects", projects.size());
                for (JiraProjectDTO project : projects) {
                    SyncResult r = syncProject(project.getKey());
                    totalSynced += r.synced();
                    totalFailed += r.failed();
                }
            } else {
                // Sync only configured project keys
                log.info("Syncing {} configured Jira projects: {}", projectKeys.size(), projectKeys);
                for (String key : projectKeys) {
                    SyncResult r = syncProject(key.trim());
                    totalSynced += r.synced();
                    totalFailed += r.failed();
                }
            }
        } catch (Exception e) {
            log.error("Jira full sync encountered a fatal error: {}", e.getMessage(), e);
        }

        return new SyncResult(totalSynced, totalFailed, "OK");
    }

    /**
     * Syncs all issues for a single Jira project key.
     *
     * @param projectKey  e.g. "PROJ"
     * @return SyncResult for this project
     */
    public SyncResult syncProject(String projectKey) {
        log.info("Syncing Jira project: {}", projectKey);
        int synced = 0;
        int failed = 0;

        try {
            List<JiraIssueDTO> issues = jiraApiClient.getIssuesForProject(projectKey);

            for (JiraIssueDTO issue : issues) {
                try {
                    ingestIssue(issue);
                    synced++;
                } catch (Exception e) {
                    log.warn("Failed to ingest Jira issue {}: {}", issue.getKey(), e.getMessage());
                    failed++;
                }
            }

        } catch (Exception e) {
            log.error("Failed to sync project {}: {}", projectKey, e.getMessage(), e);
        }

        log.info("Project {} sync complete: {} synced, {} failed", projectKey, synced, failed);
        return new SyncResult(synced, failed, "OK");
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /**
     * Converts a JiraIssueDTO to a raw JSON string and feeds it into the
     * existing EventIngestionService → JiraTicketNormalizer pipeline.
     *
     * This reuses the normalizer already built — no duplicate logic.
     */
    private void ingestIssue(JiraIssueDTO issue) throws Exception {
        // Determine event type from issue state
        String eventType = resolveEventType(issue);

        // Serialize the issue DTO to JSON — the normalizer expects a JSON string
        String rawJson = objectMapper.writeValueAsString(buildNormalizerPayload(issue));

        eventIngestionService.ingestEvent("JIRA", eventType, rawJson);
    }

    /**
     * Wraps the JiraIssueDTO fields into the Jira webhook payload shape
     * that JiraTicketNormalizer already knows how to parse.
     * Shape: { "webhookEvent": "jira:issue_updated", "issue": { ... } }
     */
    private java.util.Map<String, Object> buildNormalizerPayload(JiraIssueDTO issue) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();

        // Mirror Jira webhook payload structure so JiraTicketNormalizer reuses cleanly
        String webhookEvent = "jira:issue_updated";
        payload.put("webhookEvent", webhookEvent);
        payload.put("issue", issue); // ObjectMapper will serialize the DTO fields
        payload.put("syncSource", "JIRA_API_SYNC"); // mark as active sync vs webhook

        return payload;
    }

    /**
     * Infers TICKET_CREATED vs TICKET_UPDATED based on created/updated timestamps.
     */
    private String resolveEventType(JiraIssueDTO issue) {
        if (issue.getFields() == null) return "TICKET_UPDATED";
        String created = issue.getFields().getCreated();
        String updated = issue.getFields().getUpdated();
        // If created == updated (within a minute), treat as newly created
        if (created != null && created.equals(updated)) return "TICKET_CREATED";
        return "TICKET_UPDATED";
    }

    // -------------------------------------------------------
    // Result record
    // -------------------------------------------------------

    /**
     * Immutable result of a sync operation.
     */
    public record SyncResult(int synced, int failed, String status) {
        public boolean isSuccess() { return failed == 0; }

        @Override
        public String toString() {
            return "SyncResult{synced=" + synced + ", failed=" + failed + ", status='" + status + "'}";
        }
    }
}
