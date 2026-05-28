package com.ai.engine.backend.config;

/**
 * JiraRuntimeConfig: a simple record holding Jira credentials for the current tenant.
 * 
 * Unlike JiraConnectionConfig (which reads from static .env),
 * this is created at runtime from the database for each request.
 * 
 * See: TenantCredentialService.getJiraConfig()
 */
public record JiraRuntimeConfig(
    String baseUrl,
    String userEmail,
    String apiToken
) {
    /**
     * Check if all required credentials are present.
     */
    public boolean hasCredentials() {
        return baseUrl != null && !baseUrl.isBlank()
            && userEmail != null && !userEmail.isBlank()
            && apiToken != null && !apiToken.isBlank();
    }

    /**
     * Check if this is a valid Jira URL.
     */
    public boolean isValidUrl() {
        return baseUrl != null && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"));
    }
}
