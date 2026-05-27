package com.ai.engine.backend.service;

import com.ai.engine.backend.dto.intelligence.BottleneckDTO;
import com.ai.engine.backend.dto.intelligence.CycleTimeDTO;
import com.ai.engine.backend.dto.intelligence.VelocityDataPointDTO;
import com.ai.engine.backend.dto.intelligence.WorkloadDTO;
import com.ai.engine.backend.model.EngineeringEvent;
import com.ai.engine.backend.repository.EngineeringEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified Engineering Intelligence Service — Layer 3 of the platform architecture.
 *
 * This service queries the engineering_events table ACROSS ALL SOURCES (GitHub, Jira, Slack)
 * to produce actionable intelligence metrics. It is NOT Jira-specific.
 *
 * Output from this service is consumed by:
 *   1. EngineeringIntelligenceController  — REST API for dashboards
 *   2. AI Insight Layer (Layer 4, next phase) — as grounding context for Gemini/Spring AI
 *
 * Metrics provided:
 *   - Cycle Time      : how long tickets stay open (Jira)
 *   - Bottlenecks     : events stuck with no update beyond threshold (all sources)
 *   - Workload        : per-engineer activity breakdown across all platforms
 *   - Velocity Trend  : daily event count by source over a time window
 *   - Cross-Platform Activity : combined view per engineer
 */
@Service
public class EngineeringIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(EngineeringIntelligenceService.class);

    private final EngineeringEventRepository repository;

    public EngineeringIntelligenceService(EngineeringEventRepository repository) {
        this.repository = repository;
    }

    // -------------------------------------------------------
    // 1. CYCLE TIME — Jira ticket open → close duration
    // -------------------------------------------------------

    /**
     * Calculates average cycle time (days) for Jira tickets in a given project.
     * Cycle time = time between first TICKET_CREATED event and last TICKET_UPDATED event
     * for the same ticket key.
     *
     * @param project  Jira project name (stored in repositoryName field)
     * @return CycleTimeDTO with avg, min, max days and ticket count
     */
    public CycleTimeDTO getCycleTime(String project) {
        log.debug("Calculating cycle time for project: {}", project);

        List<Object[]> rows = repository.getJiraTicketTimestampPairs(project);

        if (rows == null || rows.isEmpty()) {
            return new CycleTimeDTO(project, 0, 0, 0, 0);
        }

        List<Double> durations = new ArrayList<>();

        for (Object[] row : rows) {
            // row[0] = ticketKey, row[1] = MIN(timestamp), row[2] = MAX(timestamp)
            if (row[1] instanceof LocalDateTime first && row[2] instanceof LocalDateTime last) {
                double days = ChronoUnit.HOURS.between(first, last) / 24.0;
                if (days >= 0) durations.add(days);
            }
        }

        if (durations.isEmpty()) {
            return new CycleTimeDTO(project, 0, 0, 0, 0);
        }

        double avg = durations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min = durations.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = durations.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        // Round to 2 decimal places
        avg = Math.round(avg * 100.0) / 100.0;
        min = Math.round(min * 100.0) / 100.0;
        max = Math.round(max * 100.0) / 100.0;

        log.info("Cycle time for project {}: avg={}d, min={}d, max={}d, tickets={}",
                project, avg, min, max, durations.size());

        return new CycleTimeDTO(project, avg, durations.size(), min, max);
    }

    // -------------------------------------------------------
    // 2. BOTTLENECK DETECTION — stuck events across all sources
    // -------------------------------------------------------

    /**
     * Detects engineering bottlenecks: events that have not progressed
     * beyond a given number of days threshold.
     *
     * Covers ALL sources:
     *   - JIRA    : tickets stuck in same status
     *   - GITHUB  : PRs with no activity (stale code push events)
     *   - SLACK   : (future: threads with no response)
     *
     * @param stuckDays  number of days without update to be considered stuck
     * @return list of BottleneckDTO sorted by most stale first
     */
    public List<BottleneckDTO> detectBottlenecks(int stuckDays) {
        log.debug("Detecting bottlenecks: stuckDays={}", stuckDays);

        LocalDateTime cutoff = LocalDateTime.now().minusDays(stuckDays);
        List<BottleneckDTO> bottlenecks = new ArrayList<>();

        for (String source : List.of("JIRA", "GITHUB", "SLACK")) {
            List<EngineeringEvent> staleEvents = repository.findStaleEvents(source, cutoff);

            for (EngineeringEvent event : staleEvents) {
                long daysSince = ChronoUnit.DAYS.between(
                        event.getTimestamp(), LocalDateTime.now());

                bottlenecks.add(new BottleneckDTO(
                        event.getSource(),
                        event.getEventType(),
                        event.getEngineerName(),
                        event.getSummary(),
                        event.getBranchName(),       // ticket key / branch / ref
                        event.getRepositoryName(),   // project / repo
                        event.getTimestamp(),
                        daysSince
                ));
            }
        }

        // Sort by most stale first
        bottlenecks.sort(Comparator.comparingLong(BottleneckDTO::getDaysSinceLastUpdate).reversed());

        log.info("Found {} bottlenecks across all sources (stuckDays={})", bottlenecks.size(), stuckDays);
        return bottlenecks;
    }

    // -------------------------------------------------------
    // 3. WORKLOAD — per-engineer activity across all platforms
    // -------------------------------------------------------

    /**
     * Returns a workload breakdown per engineer showing activity across
     * GitHub, Jira, and Slack — unified into a single view.
     *
     * This reveals: who is active on what platform, overloaded engineers,
     * and engineers who are silent across all platforms (risk signal).
     *
     * @return list of WorkloadDTO sorted by total events descending
     */
    public List<WorkloadDTO> getEngineerWorkload() {
        log.debug("Fetching cross-platform engineer workload");

        List<Object[]> rows = repository.getCrossPlatformActivityByEngineer();

        // Build a map: engineerName → WorkloadDTO
        Map<String, WorkloadDTO> workloadMap = new LinkedHashMap<>();

        for (Object[] row : rows) {
            // row[0] = engineerName, row[1] = source, row[2] = count
            String name   = row[0] != null ? (String) row[0] : "Unknown";
            String source = row[1] != null ? (String) row[1] : "UNKNOWN";
            long   count  = row[2] instanceof Long l ? l : 0L;

            workloadMap.computeIfAbsent(name, WorkloadDTO::new)
                       .addEvents(source, count);
        }

        List<WorkloadDTO> result = new ArrayList<>(workloadMap.values());
        result.sort(Comparator.comparingLong(WorkloadDTO::getTotalEvents).reversed());

        log.info("Workload computed for {} engineers", result.size());
        return result;
    }

    // -------------------------------------------------------
    // 4. VELOCITY TREND — daily event counts by source
    // -------------------------------------------------------

    /**
     * Returns daily event counts per source platform over the last N days.
     * This powers velocity/trend charts showing team throughput over time.
     *
     * @param days  number of past days to include (e.g. 14 = last 2 weeks)
     * @return list of VelocityDataPointDTO (date × source × count)
     */
    public List<VelocityDataPointDTO> getVelocityTrend(int days) {
        log.debug("Fetching velocity trend for last {} days", days);

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = repository.getDailyEventCountBySource(since);

        List<VelocityDataPointDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            // row[0] = DATE(timestamp) as java.sql.Date, row[1] = source, row[2] = count
            String date   = row[0] != null ? row[0].toString() : "unknown";
            String source = row[1] != null ? (String) row[1] : "UNKNOWN";
            long   count  = row[2] instanceof Number n ? n.longValue() : 0L;

            result.add(new VelocityDataPointDTO(date, source, count));
        }

        log.info("Velocity trend: {} data points over last {} days", result.size(), days);
        return result;
    }

    // -------------------------------------------------------
    // 5. CROSS-PLATFORM ACTIVITY SUMMARY
    // -------------------------------------------------------

    /**
     * Returns a simple flat list of per-engineer, per-source activity counts.
     * Useful for quick dashboards showing who is doing what where.
     *
     * @return list of WorkloadDTO (same as workload but without sorting by total)
     */
    public List<WorkloadDTO> getCrossPlatformActivity() {
        return getEngineerWorkload(); // same data, consistent naming for API clarity
    }
}
