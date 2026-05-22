package com.ai.engine.backend.normalization;

import com.ai.engine.backend.model.Engineer;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.normalization.impl.GitHubPushNormalizer;
import com.ai.engine.backend.normalization.impl.JiraTicketNormalizer;
import com.ai.engine.backend.normalization.impl.SlackMessageNormalizer;
import com.ai.engine.backend.service.IdentityResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class EventNormalizerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdentityResolutionService identityService;

    private GitHubPushNormalizer gitHubNormalizer;
    private JiraTicketNormalizer jiraNormalizer;
    private SlackMessageNormalizer slackNormalizer;

    @BeforeEach
    void setUp() {
        identityService = Mockito.mock(IdentityResolutionService.class);
        // Stub identity resolution to return a dummy Engineer
        when(identityService.resolveEngineer(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String name = invocation.getArgument(2);
                    return Engineer.builder().id(1L).fullName(name).build();
                });

        gitHubNormalizer = new GitHubPushNormalizer(objectMapper, identityService);
        jiraNormalizer = new JiraTicketNormalizer(objectMapper, identityService);
        slackNormalizer = new SlackMessageNormalizer(objectMapper, identityService);
    }

    @Test
    void testGitHubPushNormalization_Success() {
        String payload = """
                {
                  "ref": "refs/heads/main",
                  "repository": {
                    "name": "ai-platform"
                  },
                  "pusher": {
                    "name": "akinda-dev"
                  },
                  "commits": [
                    { "id": "c1", "message": "First commit" },
                    { "id": "c2", "message": "Second commit" }
                  ]
                }
                """;

        assertTrue(gitHubNormalizer.supports("GITHUB", "CODE_PUSH"));
        EngineeringEvent event = gitHubNormalizer.normalize(payload);

        assertEquals("GITHUB", event.getSource());
        assertEquals("CODE_PUSH", event.getEventType());
        assertEquals("akinda-dev", event.getEngineerName());
        assertEquals("main", event.getBranchName());
        assertEquals("ai-platform", event.getRepositoryName());
        assertEquals(2, event.getCommitCount());
        assertNotNull(event.getEngineer());
        assertEquals("akinda-dev", event.getEngineer().getFullName());
        assertNotNull(event.getMetadata());
        assertEquals("main", event.getMetadata().get("branch"));
    }

    @Test
    void testGitHubPushNormalization_NullSafe() {
        String payload = "{}"; // completely empty payload

        EngineeringEvent event = gitHubNormalizer.normalize(payload);

        assertEquals("GITHUB", event.getSource());
        assertEquals("CODE_PUSH", event.getEventType());
        assertEquals("Unknown Pusher", event.getEngineerName());
        assertEquals("unknown-branch", event.getBranchName());
        assertEquals("unknown-repository", event.getRepositoryName());
        assertEquals(0, event.getCommitCount());
    }

    @Test
    void testJiraTicketNormalization_Created() {
        String payload = """
                {
                  "webhookEvent": "jira:issue_created",
                  "issue": {
                    "key": "PROJ-456",
                    "fields": {
                      "summary": "Fix login crash",
                      "project": { "name": "AI Engine" },
                      "status": { "name": "To Do" },
                      "priority": { "name": "High" },
                      "assignee": {
                        "displayName": "Akinda Perera",
                        "accountId": "acc-123"
                      }
                    }
                  }
                }
                """;

        assertTrue(jiraNormalizer.supports("JIRA", "TICKET_CREATED"));
        EngineeringEvent event = jiraNormalizer.normalize(payload);

        assertEquals("JIRA", event.getSource());
        assertEquals("TICKET_CREATED", event.getEventType());
        assertEquals("Akinda Perera", event.getEngineerName());
        assertEquals("PROJ-456", event.getBranchName());
        assertEquals("AI Engine", event.getRepositoryName());
        assertNotNull(event.getMetadata());
        assertEquals("PROJ-456", event.getMetadata().get("ticketKey"));
        assertEquals("To Do", event.getMetadata().get("status"));
        assertEquals("High", event.getMetadata().get("priority"));
    }

    @Test
    void testSlackMessageNormalization() {
        String payload = """
                {
                  "event": {
                    "type": "message",
                    "user": "U12345",
                    "text": "Hello world, this is a test message from developer.",
                    "channel": "C999",
                    "ts": "16123456"
                  }
                }
                """;

        assertTrue(slackNormalizer.supports("SLACK", "MESSAGE_SENT"));
        EngineeringEvent event = slackNormalizer.normalize(payload);

        assertEquals("SLACK", event.getSource());
        assertEquals("MESSAGE_SENT", event.getEventType());
        assertEquals("U12345", event.getEngineerName());
        assertEquals("C999", event.getBranchName());
        assertTrue(event.getSummary().contains("Hello world"));
        assertNotNull(event.getMetadata());
        assertEquals("C999", event.getMetadata().get("channel"));
        assertEquals("Hello world, this is a test message from developer.", event.getMetadata().get("text"));
    }
}
