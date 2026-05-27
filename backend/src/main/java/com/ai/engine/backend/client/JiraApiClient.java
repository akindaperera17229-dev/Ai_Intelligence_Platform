package com.ai.engine.backend.client;

import com.ai.engine.backend.config.JiraConnectionConfig;
import com.ai.engine.backend.dto.jira.JiraIssueDTO;
import com.ai.engine.backend.dto.jira.JiraProjectDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * HTTP client for Jira Cloud REST API v3.
 *
 * Authentication: HTTP Basic Auth using Atlassian API Token.
 *   Header: Authorization: Basic base64(email:apiToken)
 *
 * This client is the ONLY place in the codebase that talks to Jira's external API.
 * All other services use this client — keeping HTTP concerns isolated.
 *
 * Docs: https://developer.atlassian.com/cloud/jira/platform/rest/v3/
 */
@Service
public class JiraApiClient {

    private static final Logger log = LoggerFactory.getLogger(JiraApiClient.class);
    private static final int PAGE_SIZE = 50;

    private final JiraConnectionConfig config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public JiraApiClient(JiraConnectionConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restClient = buildRestClient(config);
    }

    /**
     * Fetches ALL Jira projects the configured user has access to.
     * Handles pagination automatically.
     */
    public List<JiraProjectDTO> getProjects() {
        assertConfigured();
        List<JiraProjectDTO> all = new ArrayList<>();
        int startAt = 0;

        while (true) {
            String url = UriComponentsBuilder
                    .fromUriString(config.getBaseUrl() + "/rest/api/3/project/search")
                    .queryParam("startAt", startAt)
                    .queryParam("maxResults", PAGE_SIZE)
                    .toUriString();

            try {
                JiraProjectDTO.PageResponse page = restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(JiraProjectDTO.PageResponse.class);

                if (page == null || page.getValues() == null || page.getValues().isEmpty()) break;

                all.addAll(page.getValues());
                log.debug("Fetched {} projects (startAt={})", page.getValues().size(), startAt);

                if (page.isLast()) break;
                startAt += PAGE_SIZE;

            } catch (Exception e) {
                log.error("Failed to fetch Jira projects at startAt={}: {}", startAt, e.getMessage());
                break;
            }
        }

        log.info("Fetched total {} Jira projects", all.size());
        return all;
    }

    /**
     * Fetches all issues for a given Jira project key using JQL.
     * Handles pagination automatically.
     *
     * @param projectKey  e.g. "PROJ"
     * @return list of all issues (up to Jira API limits)
     */
    public List<JiraIssueDTO> getIssuesForProject(String projectKey) {
        assertConfigured();
        List<JiraIssueDTO> all = new ArrayList<>();
        int startAt = 0;

        // Curated fields to minimize payload size
        String fields = "summary,status,priority,assignee,reporter,project,created,updated,customfield_10016,sprint";
        String jql = "project=" + projectKey + " ORDER BY updated DESC";

        while (true) {
            String url = UriComponentsBuilder
                    .fromUriString(config.getBaseUrl() + "/rest/api/3/search")
                    .queryParam("jql", jql)
                    .queryParam("fields", fields)
                    .queryParam("startAt", startAt)
                    .queryParam("maxResults", PAGE_SIZE)
                    .toUriString();

            try {
                JiraIssueDTO.SearchResult result = restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(JiraIssueDTO.SearchResult.class);

                if (result == null || result.getIssues() == null || result.getIssues().isEmpty()) break;

                all.addAll(result.getIssues());
                log.debug("Fetched {} issues for project {} (startAt={})",
                        result.getIssues().size(), projectKey, startAt);

                if (startAt + PAGE_SIZE >= result.getTotal()) break;
                startAt += PAGE_SIZE;

            } catch (Exception e) {
                log.error("Failed to fetch issues for project {} at startAt={}: {}",
                        projectKey, startAt, e.getMessage());
                break;
            }
        }

        log.info("Fetched total {} issues for project {}", all.size(), projectKey);
        return all;
    }

    /**
     * Fetches a single Jira issue by its key.
     *
     * @param issueKey  e.g. "PROJ-42"
     * @return the issue DTO, or null if not found
     */
    public JiraIssueDTO getIssue(String issueKey) {
        assertConfigured();
        String url = config.getBaseUrl() + "/rest/api/3/issue/" + issueKey
                + "?fields=summary,status,priority,assignee,reporter,project,created,updated,customfield_10016";

        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JiraIssueDTO.class);
        } catch (Exception e) {
            log.error("Failed to fetch Jira issue {}: {}", issueKey, e.getMessage());
            return null;
        }
    }

    /**
     * Checks connectivity to Jira by fetching the server info endpoint.
     * Useful for health checks and startup validation.
     *
     * @return true if connection is successful
     */
    public boolean testConnection() {
        if (!config.hasCredentials()) return false;
        try {
            restClient.get()
                    .uri(config.getBaseUrl() + "/rest/api/3/serverInfo")
                    .retrieve()
                    .body(String.class);
            log.info("Jira connection test successful for: {}", config.getBaseUrl());
            return true;
        } catch (Exception e) {
            log.warn("Jira connection test failed: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /**
     * Builds a pre-configured RestClient with Basic Auth header injected on every request.
     */
    private RestClient buildRestClient(JiraConnectionConfig cfg) {
        String credentials = cfg.getUserEmail() + ":" + cfg.getApiToken();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "Basic " + encoded;

        return RestClient.builder()
                .defaultHeader("Authorization", authHeader)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private void assertConfigured() {
        if (!config.hasCredentials()) {
            throw new IllegalStateException(
                "Jira is not configured. Set JIRA_BASE_URL, JIRA_USER_EMAIL, JIRA_API_TOKEN " +
                "in your environment.");
        }
    }
}
