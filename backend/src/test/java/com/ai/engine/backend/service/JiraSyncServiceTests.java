package com.ai.engine.backend.service;

import com.ai.engine.backend.client.JiraApiClient;
import com.ai.engine.backend.config.JiraConnectionConfig;
import com.ai.engine.backend.config.JiraRuntimeConfig;
import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.dto.jira.JiraIssueDTO;
import com.ai.engine.backend.dto.jira.JiraProjectDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JiraSyncServiceTests {

    private JiraApiClient jiraApiClient;
    private JiraConnectionConfig config;
    private TenantCredentialService credentialService;
    private TenantContext tenantContext;
    private EventIngestionService eventIngestionService;
    private ObjectMapper objectMapper;
    private JiraSyncService service;

    @BeforeEach
    void setUp() {
        jiraApiClient = mock(JiraApiClient.class);
        config = mock(JiraConnectionConfig.class);
        credentialService = mock(TenantCredentialService.class);
        tenantContext = mock(TenantContext.class);
        eventIngestionService = mock(EventIngestionService.class);
        objectMapper = new ObjectMapper();
        service = new JiraSyncService(
                jiraApiClient,
                config,
                credentialService,
                tenantContext,
                eventIngestionService,
                objectMapper
        );
    }

    @Test
    void testSyncAllProjects_NotConfigured() {
        when(tenantContext.hasTenant()).thenReturn(true);
        when(tenantContext.getCurrentTenantId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(credentialService.getJiraConfig()).thenReturn(new JiraRuntimeConfig("", "", ""));

        JiraSyncService.SyncResult result = service.syncAllProjects();

        assertEquals("Credentials not configured", result.status());
        assertEquals(0, result.synced());
        verifyNoInteractions(jiraApiClient);
    }

    @Test
    void testSyncAllProjects_Success_AllProjects() {
        when(tenantContext.hasTenant()).thenReturn(true);
        when(tenantContext.getCurrentTenantId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(credentialService.getJiraConfig()).thenReturn(
                new JiraRuntimeConfig("https://example.atlassian.net", "user@example.com", "token")
        );

        JiraConnectionConfig.Sync syncSettings = new JiraConnectionConfig.Sync();
        syncSettings.setProjects(""); // empty means sync all
        when(config.getSync()).thenReturn(syncSettings);

        JiraProjectDTO project = new JiraProjectDTO();
        project.setKey("PROJ");
        project.setName("Project Test");
        when(jiraApiClient.getProjects(any(JiraRuntimeConfig.class))).thenReturn(List.of(project));

        JiraIssueDTO issue = new JiraIssueDTO();
        issue.setKey("PROJ-1");
        JiraIssueDTO.Fields fields = new JiraIssueDTO.Fields();
        fields.setSummary("Issue Test");
        issue.setFields(fields);
        when(jiraApiClient.getIssuesForProject(eq("PROJ"), any(JiraRuntimeConfig.class))).thenReturn(List.of(issue));

        JiraSyncService.SyncResult result = service.syncAllProjects();

        assertEquals("OK", result.status());
        assertEquals(1, result.synced());
        assertEquals(0, result.failed());
        verify(eventIngestionService).ingestEvent(eq("JIRA"), anyString(), anyString());
    }
}
