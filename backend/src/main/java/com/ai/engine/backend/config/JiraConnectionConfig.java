package com.ai.engine.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Jira Cloud REST API connection.
 * All values are loaded from environment variables — no secrets in source code.
 *
 * Required env vars (when jira.sync.enabled=true):
 *   JIRA_BASE_URL       — e.g. https://yourcompany.atlassian.net
 *   JIRA_USER_EMAIL     — Atlassian account email
 *   JIRA_API_TOKEN      — API token from id.atlassian.com/manage-profile/security/api-tokens
 *
 * Optional:
 *   JIRA_SYNC_ENABLED   — default: false (safe default, no accidental syncs)
 *   JIRA_SYNC_PROJECTS  — comma-separated project keys, e.g. PROJ1,PROJ2 (empty = all projects)
 *   JIRA_SYNC_CRON      — default: nightly at 2 AM
 */
@Configuration
@ConfigurationProperties(prefix = "jira")
public class JiraConnectionConfig {

    private String baseUrl = "";
    private String userEmail = "";
    private String apiToken = "";
    private Sync sync = new Sync();

    /**
     * Returns true if all required credentials (URL, email, and API token) are configured.
     */
    public boolean hasCredentials() {
        return baseUrl != null && !baseUrl.isBlank()
                && userEmail != null && !userEmail.isBlank()
                && apiToken != null && !apiToken.isBlank();
    }

    /**
     * Returns true only if credentials are configured and scheduled sync is enabled.
     */
    public boolean isConfigured() {
        return sync.isEnabled() && hasCredentials();
    }

    // --- Nested Sync config ---

    public static class Sync {
        private boolean enabled = false;
        private String projects = "";
        private String cron = "0 0 2 * * *";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getProjects() { return projects; }
        public void setProjects(String projects) { this.projects = projects; }

        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }

        /**
         * Returns list of configured project keys, or empty list if all projects should be synced.
         */
        public java.util.List<String> getProjectKeyList() {
            if (projects == null || projects.isBlank()) return java.util.List.of();
            return java.util.Arrays.asList(projects.split(","));
        }
    }

    // --- Getters & Setters ---

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public Sync getSync() { return sync; }
    public void setSync(Sync sync) { this.sync = sync; }
}
