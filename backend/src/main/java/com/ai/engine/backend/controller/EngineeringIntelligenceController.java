package com.ai.engine.backend.controller;

import com.ai.engine.backend.dto.intelligence.BottleneckDTO;
import com.ai.engine.backend.dto.intelligence.CycleTimeDTO;
import com.ai.engine.backend.dto.intelligence.VelocityDataPointDTO;
import com.ai.engine.backend.dto.intelligence.WorkloadDTO;
import com.ai.engine.backend.service.EngineeringIntelligenceService;
import com.ai.engine.backend.service.JiraSyncService;
import com.ai.engine.backend.client.JiraApiClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for unified Engineering Intelligence and Jira Sync operations.
 * Exposes endpoints for Layer 3 (Intelligence Layer) of the application.
 */
@RestController
@RequestMapping("/api/intelligence")
@CrossOrigin
public class EngineeringIntelligenceController {

    private final EngineeringIntelligenceService intelligenceService;
    private final JiraSyncService jiraSyncService;
    private final JiraApiClient jiraApiClient;

    public EngineeringIntelligenceController(
            EngineeringIntelligenceService intelligenceService,
            JiraSyncService jiraSyncService,
            JiraApiClient jiraApiClient) {
        this.intelligenceService = intelligenceService;
        this.jiraSyncService = jiraSyncService;
        this.jiraApiClient = jiraApiClient;
    }

    /**
     * GET /api/intelligence/cycle-time?project=KEY
     * Calculates the cycle time statistics for a project key (repositoryName field).
     */
    @GetMapping("/cycle-time")
    public CycleTimeDTO getCycleTime(@RequestParam String project) {
        return intelligenceService.getCycleTime(project);
    }

    /**
     * GET /api/intelligence/bottlenecks?stuckDays=3
     * Returns a list of stale events across all platforms that have been stuck for at least stuckDays.
     */
    @GetMapping("/bottlenecks")
    public List<BottleneckDTO> getBottlenecks(@RequestParam(defaultValue = "3") int stuckDays) {
        return intelligenceService.detectBottlenecks(stuckDays);
    }

    /**
     * GET /api/intelligence/workload
     * Returns workload and event count breakdown per engineer across all integrated platforms.
     */
    @GetMapping("/workload")
    public List<WorkloadDTO> getWorkload() {
        return intelligenceService.getEngineerWorkload();
    }

    /**
     * GET /api/intelligence/velocity?days=14
     * Returns daily activity velocity trend data points grouped by source platform.
     */
    @GetMapping("/velocity")
    public List<VelocityDataPointDTO> getVelocity(@RequestParam(defaultValue = "14") int days) {
        return intelligenceService.getVelocityTrend(days);
    }

    /**
     * GET /api/intelligence/activity
     * Returns activity stats for engineers (equivalent to workload).
     */
    @GetMapping("/activity")
    public List<WorkloadDTO> getActivity() {
        return intelligenceService.getCrossPlatformActivity();
    }

    /**
     * POST /api/intelligence/jira/sync
     * Triggers active synchronisation of all configured/accessible Jira projects.
     */
    @PostMapping("/jira/sync")
    public JiraSyncService.SyncResult triggerJiraSync() {
        return jiraSyncService.syncAllProjects();
    }

    /**
     * GET /api/intelligence/jira/status
     * Tests connectivity to the Jira Cloud API using current credentials.
     */
    @GetMapping("/jira/status")
    public boolean getJiraConnectionStatus() {
        return jiraApiClient.testConnection();
    }
}
