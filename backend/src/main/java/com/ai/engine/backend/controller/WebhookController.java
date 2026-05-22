package com.ai.engine.backend.controller;

import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.service.EventIngestionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
@CrossOrigin
public class WebhookController {

    private final EventIngestionService service;

    public WebhookController(EventIngestionService service) {
        this.service = service;
    }

    @PostMapping("/github")
    public EngineeringEvent receiveGitHubWebhook(@RequestBody String payload) {
        return service.ingestEvent("GITHUB", "CODE_PUSH", payload);
    }

    @PostMapping("/jira")
    public EngineeringEvent receiveJiraWebhook(@RequestBody String payload) {
        return service.ingestEvent("JIRA", "TICKET_UPDATED", payload);
    }

    @PostMapping("/slack")
    public EngineeringEvent receiveSlackWebhook(@RequestBody String payload) {
        return service.ingestEvent("SLACK", "MESSAGE_SENT", payload);
    }
}

