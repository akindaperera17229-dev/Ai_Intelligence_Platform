package com.ai.engine.backend.service;

import com.ai.engine.backend.dto.intelligence.BottleneckDTO;
import com.ai.engine.backend.dto.intelligence.CycleTimeDTO;
import com.ai.engine.backend.dto.intelligence.VelocityDataPointDTO;
import com.ai.engine.backend.dto.intelligence.WorkloadDTO;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.repository.EngineeringEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class EngineeringIntelligenceServiceTests {

    private EngineeringEventRepository repository;
    private EngineeringIntelligenceService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(EngineeringEventRepository.class);
        service = new EngineeringIntelligenceService(repository);
    }

    @Test
    void testGetCycleTime_Success() {
        LocalDateTime created = LocalDateTime.of(2026, 5, 20, 10, 0);
        LocalDateTime completed = LocalDateTime.of(2026, 5, 22, 10, 0); // exactly 2 days (48 hours)

        Object[] pair1 = new Object[]{"PROJ-1", created, completed};
        List<Object[]> pairsList = Arrays.asList(new Object[][]{ pair1 });
        when(repository.getJiraTicketTimestampPairs("PROJ")).thenReturn(pairsList);

        CycleTimeDTO dto = service.getCycleTime("PROJ");

        assertEquals("PROJ", dto.getProject());
        assertEquals(2.0, dto.getAvgDays());
        assertEquals(1, dto.getTicketCount());
        assertEquals(2.0, dto.getMinDays());
        assertEquals(2.0, dto.getMaxDays());
    }

    @Test
    void testGetCycleTime_NoData() {
        List<Object[]> emptyList = Collections.emptyList();
        when(repository.getJiraTicketTimestampPairs("PROJ")).thenReturn(emptyList);

        CycleTimeDTO dto = service.getCycleTime("PROJ");

        assertEquals("PROJ", dto.getProject());
        assertEquals(0.0, dto.getAvgDays());
        assertEquals(0, dto.getTicketCount());
    }

    @Test
    void testDetectBottlenecks() {
        LocalDateTime now = LocalDateTime.now();
        EngineeringEvent jiraEvent = new EngineeringEvent();
        jiraEvent.setSource("JIRA");
        jiraEvent.setEventType("TICKET_CREATED");
        jiraEvent.setEngineerName("Akinda");
        jiraEvent.setSummary("Ticket 1");
        jiraEvent.setBranchName("PROJ-1");
        jiraEvent.setRepositoryName("PROJ");
        jiraEvent.setTimestamp(now.minusDays(5));

        EngineeringEvent githubEvent = new EngineeringEvent();
        githubEvent.setSource("GITHUB");
        githubEvent.setEventType("CODE_PUSH");
        githubEvent.setEngineerName("Akinda");
        githubEvent.setSummary("Push 1");
        githubEvent.setBranchName("main");
        githubEvent.setRepositoryName("repo-1");
        githubEvent.setTimestamp(now.minusDays(10));

        when(repository.findStaleEvents(eq("JIRA"), any(LocalDateTime.class))).thenReturn(List.of(jiraEvent));
        when(repository.findStaleEvents(eq("GITHUB"), any(LocalDateTime.class))).thenReturn(List.of(githubEvent));
        when(repository.findStaleEvents(eq("SLACK"), any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        List<BottleneckDTO> bottlenecks = service.detectBottlenecks(3);

        assertEquals(2, bottlenecks.size());
        // GitHub should be first because it is older (10 days vs 5 days)
        assertEquals("GITHUB", bottlenecks.get(0).getSource());
        assertEquals(10, bottlenecks.get(0).getDaysSinceLastUpdate());
        assertEquals("JIRA", bottlenecks.get(1).getSource());
        assertEquals(5, bottlenecks.get(1).getDaysSinceLastUpdate());
    }

    @Test
    void testGetEngineerWorkload() {
        Object[] row1 = new Object[]{"Akinda", "GITHUB", 10L};
        Object[] row2 = new Object[]{"Akinda", "JIRA", 5L};
        Object[] row3 = new Object[]{"John", "SLACK", 20L};

        when(repository.getCrossPlatformActivityByEngineer()).thenReturn(Arrays.asList(row1, row2, row3));

        List<WorkloadDTO> workload = service.getEngineerWorkload();

        assertEquals(2, workload.size());

        // John should be first since totalEvents is 20 (Akinda's total is 15)
        assertEquals("John", workload.get(0).getEngineerName());
        assertEquals(20, workload.get(0).getTotalEvents());
        assertEquals(20, workload.get(0).getSlackMessages());
        assertEquals(0, workload.get(0).getGithubEvents());

        assertEquals("Akinda", workload.get(1).getEngineerName());
        assertEquals(15, workload.get(1).getTotalEvents());
        assertEquals(10, workload.get(1).getGithubEvents());
        assertEquals(5, workload.get(1).getJiraTickets());
    }

    @Test
    void testGetVelocityTrend() {
        java.sql.Date dateVal = java.sql.Date.valueOf("2026-05-25");
        Object[] row1 = new Object[]{dateVal, "GITHUB", 15L};
        Object[] row2 = new Object[]{dateVal, "JIRA", 3L};

        when(repository.getDailyEventCountBySource(any(LocalDateTime.class))).thenReturn(Arrays.asList(row1, row2));

        List<VelocityDataPointDTO> trend = service.getVelocityTrend(14);

        assertEquals(2, trend.size());
        assertEquals("2026-05-25", trend.get(0).getDate());
        assertEquals("GITHUB", trend.get(0).getSource());
        assertEquals(15, trend.get(0).getCount());
    }
}
