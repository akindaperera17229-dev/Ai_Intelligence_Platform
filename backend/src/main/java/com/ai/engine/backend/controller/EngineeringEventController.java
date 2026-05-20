package com.ai.engine.backend.controller;

import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.repository.EngineeringEventRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@CrossOrigin
public class EngineeringEventController {

    private final EngineeringEventRepository repository;

    public EngineeringEventController(EngineeringEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<EngineeringEvent> getAllEvents() {
        return repository.findAll();
    }
}