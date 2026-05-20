package com.ai.engine.backend.controller;

import com.ai.engine.backend.dto.GitHubPushEventDTO;
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
    public EngineeringEvent receiveGitHubWebhook(
        @RequestBody GitHubPushEventDTO payload
    ){
        return service.processGitHubPush(payload);

    }
    
    
}
