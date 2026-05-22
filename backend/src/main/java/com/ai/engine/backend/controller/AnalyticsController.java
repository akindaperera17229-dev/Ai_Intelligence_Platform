package com.ai.engine.backend.controller;

import com.ai.engine.backend.service.AnalyticsService;
import com.ai.engine.backend.dto.EngineerActivityDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(
            AnalyticsService service
    ) {
        this.service = service;
    }

    /*
     * Engineer leaderboard
     */
    @GetMapping("/engineers/activity")
    public List<EngineerActivityDTO>
    getEngineerActivity() {

        return service.getEngineerActivity();
    }
}