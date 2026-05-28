package com.ai.engine.backend.controller;

import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.service.TenantCredentialService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TenantSettingsController: REST API for managing per-tenant settings and integrations.
 * 
 * Allows users to:
 * - Configure Jira credentials (base URL, email, API token)
 * - Configure GitHub webhook secret
 * - Check integration status (without exposing secret values)
 * - Get unique webhook URLs for each tenant
 * 
 * All endpoints are authenticated (require valid JWT).
 * All credentials are encrypted and stored per-tenant.
 */
@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@Slf4j
public class TenantSettingsController {

    @Autowired
    private TenantCredentialService credentialService;

    @Autowired
    private TenantContext tenantContext;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    /**
     * GET /api/settings/integrations
     * 
     * Returns the configuration status of all integrations for the current tenant.
     * Does NOT return actual credential values — only true/false for each integration.
     */
    @GetMapping("/integrations")
    public ResponseEntity<IntegrationStatusResponse> getIntegrationStatus() {
        UUID tenantId = tenantContext.getCurrentTenantId();
        log.debug("Fetching integration status for tenant: {}", tenantId);

        return ResponseEntity.ok(new IntegrationStatusResponse(
            credentialService.isConfigured("JIRA"),
            credentialService.isConfigured("GITHUB"),
            credentialService.isConfigured("SLACK")
        ));
    }

    /**
     * POST /api/settings/integrations/jira
     * 
     * Save Jira credentials for the current tenant.
     * Credentials are encrypted before storing in the database.
     * 
     * Request body:
     * {
     *   "baseUrl": "https://mycompany.atlassian.net",
     *   "userEmail": "engineer@mycompany.com",
     *   "apiToken": "ATATT..."
     * }
     */
    @PostMapping("/integrations/jira")
    public ResponseEntity<Map<String, String>> saveJiraCredentials(@RequestBody JiraSetupRequest req) {
        UUID tenantId = tenantContext.getCurrentTenantId();
        log.info("Saving Jira credentials for tenant: {}", tenantId);

        try {
            credentialService.save("JIRA", "base_url", req.baseUrl());
            credentialService.save("JIRA", "user_email", req.userEmail());
            credentialService.save("JIRA", "api_token", req.apiToken());

            log.info("Jira credentials successfully saved for tenant: {}", tenantId);
            return ResponseEntity.ok(Map.of("status", "Jira credentials saved"));
        } catch (Exception e) {
            log.error("Failed to save Jira credentials for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to save credentials: " + e.getMessage()));
        }
    }

    /**
     * POST /api/settings/integrations/github
     * 
     * Save GitHub webhook secret for the current tenant.
     * This secret is used to verify GitHub webhook payloads.
     * 
     * Request body:
     * {
     *   "webhookSecret": "whsec_..."
     * }
     */
    @PostMapping("/integrations/github")
    public ResponseEntity<Map<String, String>> saveGitHubCredentials(@RequestBody GitHubSetupRequest req) {
        UUID tenantId = tenantContext.getCurrentTenantId();
        log.info("Saving GitHub webhook secret for tenant: {}", tenantId);

        try {
            credentialService.save("GITHUB", "webhook_secret", req.webhookSecret());

            log.info("GitHub webhook secret successfully saved for tenant: {}", tenantId);
            return ResponseEntity.ok(Map.of("status", "GitHub webhook secret saved"));
        } catch (Exception e) {
            log.error("Failed to save GitHub webhook secret for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to save webhook secret: " + e.getMessage()));
        }
    }

    /**
     * GET /api/settings/webhook-url/github
     * 
     * Returns the unique webhook URL for this tenant.
     * Users configure this URL in their GitHub/Jira settings.
     * 
     * The URL includes the tenantId so the webhook handler knows which tenant it's for.
     */
    @GetMapping("/webhook-url/{platform}")
    public ResponseEntity<WebhookUrlResponse> getWebhookUrl(@PathVariable String platform) {
        UUID tenantId = tenantContext.getCurrentTenantId();
        String webhookUrl = appBaseUrl + "/webhooks/" + platform.toLowerCase()
                          + "?tenantId=" + tenantId;

        log.debug("Generated webhook URL for {}: {}", platform, webhookUrl);
        return ResponseEntity.ok(new WebhookUrlResponse(webhookUrl));
    }

    // -------------------------------------------------------
    // Request/Response DTOs
    // -------------------------------------------------------

    /**
     * Integration status response (no secrets exposed).
     */
    public record IntegrationStatusResponse(
        @JsonProperty("jira_configured")
        boolean jiraConfigured,

        @JsonProperty("github_configured")
        boolean githubConfigured,

        @JsonProperty("slack_configured")
        boolean slackConfigured
    ) {}

    /**
     * Jira setup request.
     */
    public record JiraSetupRequest(
        @JsonProperty("base_url")
        String baseUrl,

        @JsonProperty("user_email")
        String userEmail,

        @JsonProperty("api_token")
        String apiToken
    ) {}

    /**
     * GitHub setup request.
     */
    public record GitHubSetupRequest(
        @JsonProperty("webhook_secret")
        String webhookSecret
    ) {}

    /**
     * Webhook URL response.
     */
    public record WebhookUrlResponse(
        @JsonProperty("url")
        String url
    ) {}
}
