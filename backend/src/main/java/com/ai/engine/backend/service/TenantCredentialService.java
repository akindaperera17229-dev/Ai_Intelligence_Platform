package com.ai.engine.backend.service;

import com.ai.engine.backend.config.JiraRuntimeConfig;
import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.model.TenantCredential;
import com.ai.engine.backend.repository.TenantCredentialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * TenantCredentialService: manages per-tenant encrypted credentials.
 * 
 * Replaces the static JiraConnectionConfig for runtime credential lookups.
 * All credential values are stored encrypted in the database and decrypted on-demand.
 * 
 * Usage:
 *     credentialService.save("JIRA", "api_token", "abc123");        // saves encrypted
 *     credentialService.get("JIRA", "api_token");                   // returns decrypted
 *     if (credentialService.isConfigured("JIRA")) { ... }           // checks if ready
 */
@Service
@Slf4j
public class TenantCredentialService {

    @Autowired
    private TenantCredentialRepository repository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private TenantContext tenantContext;

    /**
     * Get a decrypted credential value for the current tenant.
     * Returns empty if not found or not configured.
     */
    public Optional<String> get(String platform, String key) {
        UUID tenantId = tenantContext.getCurrentTenantId();
        return repository.findByTenantIdAndPlatformAndCredentialKey(tenantId, platform, key)
            .map(cred -> {
                try {
                    return encryptionService.decrypt(cred.getCredentialValue());
                } catch (Exception e) {
                    log.error("Failed to decrypt credential {}/{} for tenant {}", 
                        platform, key, tenantId);
                    return null;
                }
            })
            .filter(value -> value != null);
    }

    /**
     * Save or update a credential for the current tenant (encrypts before storing).
     */
    public void save(String platform, String key, String value) {
        UUID tenantId = tenantContext.getCurrentTenantId();
        String encrypted = encryptionService.encrypt(value);

        TenantCredential cred = repository
            .findByTenantIdAndPlatformAndCredentialKey(tenantId, platform, key)
            .orElse(TenantCredential.builder()
                .tenantId(tenantId)
                .platform(platform)
                .credentialKey(key)
                .build());

        cred.setCredentialValue(encrypted);
        repository.save(cred);
        log.debug("Saved credential {}/{} for tenant {}", platform, key, tenantId);
    }

    /**
     * Check if a platform is fully configured for the current tenant.
     * Returns true only if ALL required credentials for that platform are present.
     */
    public boolean isConfigured(String platform) {
        return switch (platform) {
            case "JIRA" ->
                get("JIRA", "base_url").isPresent()
                && get("JIRA", "api_token").isPresent()
                && get("JIRA", "user_email").isPresent();

            case "GITHUB" ->
                get("GITHUB", "webhook_secret").isPresent();

            case "SLACK" ->
                get("SLACK", "bot_token").isPresent();

            default -> false;
        };
    }

    /**
     * Build a JiraRuntimeConfig for the current tenant (replaces static JiraConnectionConfig).
     * Use this when you need to make Jira API calls.
     */
    public JiraRuntimeConfig getJiraConfig() {
        String baseUrl = get("JIRA", "base_url").orElse("");
        String userEmail = get("JIRA", "user_email").orElse("");
        String apiToken = get("JIRA", "api_token").orElse("");

        return new JiraRuntimeConfig(baseUrl, userEmail, apiToken);
    }

    /**
     * Get GitHub webhook secret for the current tenant.
     */
    public Optional<String> getGitHubWebhookSecret() {
        return get("GITHUB", "webhook_secret");
    }
}
