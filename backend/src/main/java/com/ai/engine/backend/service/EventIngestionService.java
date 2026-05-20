package com.ai.engine.backend.service;

import com.ai.engine.backend.dto.GitHubPushEventDTO;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.repository.EngineeringEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Service
public class EventIngestionService {

    private final EngineeringEventRepository repository;

    public EventIngestionService(EngineeringEventRepository repository) {
        this.repository = repository;
    }

    public EngineeringEvent processGitHubPush(GitHubPushEventDTO dto) {

        EngineeringEvent event = new EngineeringEvent();

        event.setEventType("CODE_PUSH");
        event.setSource("GITHUB");
        event.setEngineerName(dto.getPusher().getName());
        event.setUserId(dto.getPusher().getName());
        event.setTimestamp(LocalDateTime.now());

        return repository.save(event);
    }
}

//github webhook test