package com.ai.engine.backend.controller;

import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.repository.EngineeringEventRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@CrossOrigin
public class EngineeringEventController {

    private final EngineeringEventRepository repository;
    private final TenantContext tenantContext;

    public EngineeringEventController(
            EngineeringEventRepository repository,
            TenantContext tenantContext
    ) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public List<EngineeringEvent> getAllEvents() {
        return repository.findByTenantIdOrderByTimestampDesc(tenantContext.getCurrentTenantId());
    }
}
