package com.ai.engine.backend.service;

import com.ai.engine.backend.client.JiraApiClient;
import com.ai.engine.backend.config.JiraConnectionConfig;
import com.ai.engine.backend.config.JiraRuntimeConfig;
import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.dto.jira.JiraIssueDTO;
import com.ai.engine.backend.dto.jira.JiraProjectDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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
 * 
 * Per-Tenant Support:
 *   - For HTTP requests: TenantCredentialService reads from database per tenant
 *   - For scheduled tasks: Falls back to static JiraConnectionConfig (default tenant)
 */
@Service
@Slf4j
public class JiraSyncService {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JiraApiClient jiraApiClient;
    private final JiraConnectionConfig staticConfig;           // Fallback for default tenant
    private final TenantCredentialService credentialService;  // Runtime per-tenant credentials
    private final TenantContext tenantContext;
    private final EventIngestionService eventIngestionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public JiraSyncService(
            JiraApiClient jiraApiClient,
            JiraConnectionConfig staticConfig,
            TenantCredentialService credentialService,
            TenantContext tenantContext,
            EventIngestionService eventIngestionService,
            ObjectMapper objectMapper) {
        this.jiraApiClient = jiraApiClient;
        this.staticConfig = staticConfig;
        this.credentialService = credentialService;
        this.tenantContext = tenantContext;
        this.eventIngestionService = eventIngestionService;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------
    // Scheduled full sync — nightly at 2 AM by default
    // Configurable via jira.sync.cron env var
    // Falls back to static config for backward compatibility
    // -------------------------------------------------------

    @Scheduled(cron = "${jira.sync.cron:0 0 2 * * *}")
    public void scheduledSync() {
        // For scheduled tasks, use static config (default tenant)
        if (!staticConfig.isConfigured()) {
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
    // Uses per-tenant credentials if available
    // -------------------------------------------------------

    /**
     * Performs a full sync of all configured Jira projects for the current tenant.
     * If jira.sync.projects is set, only those project keys are synced.
     * Otherwise, all accessible projects are synced.
     *
     * @return SyncResult with counts of synced and failed issues
     */
    public SyncResult syncAllProjects() {
        // Try to get per-tenant config from database first
        JiraRuntimeConfig runtimeConfig = getRuntimeConfig();
        
        if (!runtimeConfig.hasCredentials()) {
            log.warn("Jira sync aborted — credentials not configured for tenant {}", 
                currentTenantIdOrDefault());
            return new SyncResult(0, 0, "Credentials not configured");
        }

        int totalSynced = 0;
        int totalFailed = 0;

        try {
            List<String> projectKeys = staticConfig.getSync().getProjectKeyList();

            if (projectKeys.isEmpty()) {
                // Sync all accessible projects
                List<JiraProjectDTO> projects = jiraApiClient.getProjects(runtimeConfig);
                log.info("Syncing all {} accessible Jira projects for tenant {}", 
                    projects.size(), currentTenantIdOrDefault());
                for (JiraProjectDTO project : projects) {
                    SyncResult r = syncProject(project.getKey(), runtimeConfig);
                    totalSynced += r.synced();
                    totalFailed += r.failed();
                }
            } else {
                // Sync only configured project keys
                log.info("Syncing {} configured Jira projects: {}", projectKeys.size(), projectKeys);
                for (String key : projectKeys) {
                    SyncResult r = syncProject(key.trim(), runtimeConfig);
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
    private SyncResult syncProject(String projectKey, JiraRuntimeConfig config) {
        log.info("Syncing Jira project: {} for tenant {}", projectKey, currentTenantIdOrDefault());
        int synced = 0;
        int failed = 0;

        try {
            List<JiraIssueDTO> issues = jiraApiClient.getIssuesForProject(projectKey, config);

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
     * Get the appropriate Jira runtime config for the current tenant.
     * Tries per-tenant database credentials first, falls back to static config.
     */
    private JiraRuntimeConfig getRuntimeConfig() {
        // Try to get from database (per-tenant)
        if (hasTenantContext()) {
            try {
                return credentialService.getJiraConfig();
            } catch (Exception e) {
                log.debug("Failed to load per-tenant Jira config: {}", e.getMessage());
            }
        }
        
        // Fall back to static config
        return new JiraRuntimeConfig(
            staticConfig.getBaseUrl(),
            staticConfig.getUserEmail(),
            staticConfig.getApiToken()
        );
    }

    private boolean hasTenantContext() {
        try {
            return tenantContext.hasTenant();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private UUID currentTenantIdOrDefault() {
        try {
            if (tenantContext.hasTenant()) {
                return tenantContext.getCurrentTenantId();
            }
        } catch (RuntimeException ignored) {
            // Scheduled syncs run outside an HTTP request, so use the seeded default tenant.
        }
        return DEFAULT_TENANT_ID;
    }

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
