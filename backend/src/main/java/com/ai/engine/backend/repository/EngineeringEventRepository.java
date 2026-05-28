package com.ai.engine.backend.repository;

import com.ai.engine.backend.model.EngineeringEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EngineeringEventRepository extends JpaRepository<EngineeringEvent, Long> {

    List<EngineeringEvent> findByTenantIdOrderByTimestampDesc(UUID tenantId);

    // -------------------------------------------------------
    // Existing queries — TENANT-SCOPED
    // All queries now filter by tenant_id to isolate data
    // -------------------------------------------------------

    @Query("""
            SELECT e.engineerName, COUNT(e)
            FROM EngineeringEvent e
            WHERE e.tenantId = :tenantId
            GROUP BY e.engineerName
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> getEngineerActivityStats(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT e.repositoryName, COUNT(e)
            FROM EngineeringEvent e
            WHERE e.tenantId = :tenantId
            GROUP BY e.repositoryName
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> getRepositoryStats(@Param("tenantId") UUID tenantId);

    // -------------------------------------------------------
    // Intelligence Layer — Bottleneck Detection
    // Finds events from any source older than a cutoff time
    // Used to identify: stuck Jira tickets, stale PRs, unresponded Slack threads
    // -------------------------------------------------------

    @Query("""
            SELECT e FROM EngineeringEvent e
            WHERE e.tenantId = :tenantId
            AND e.source = :source
            AND e.timestamp < :cutoff
            AND e.eventType NOT IN ('TICKET_CLOSED', 'PR_MERGED', 'PR_CLOSED')
            ORDER BY e.timestamp ASC
            """)
    List<EngineeringEvent> findStaleEvents(
            @Param("tenantId") UUID tenantId,
            @Param("source") String source,
            @Param("cutoff") LocalDateTime cutoff
    );

    // -------------------------------------------------------
    // Intelligence Layer — Cross-Platform Workload
    // Returns per-engineer event counts broken down by source platform
    // -------------------------------------------------------

    @Query("""
            SELECT e.engineerName, e.source, COUNT(e)
            FROM EngineeringEvent e
            WHERE e.tenantId = :tenantId
            AND e.engineerName IS NOT NULL
            GROUP BY e.engineerName, e.source
            ORDER BY e.engineerName, e.source
            """)
    List<Object[]> getCrossPlatformActivityByEngineer(@Param("tenantId") UUID tenantId);

    // -------------------------------------------------------
    // Intelligence Layer — Daily Velocity Trend
    // Returns event counts per day per source for the last N days
    // Used to chart: commit frequency, ticket throughput, message volume
    // -------------------------------------------------------

    @Query(value = """
            SELECT DATE(timestamp), source, COUNT(*)
            FROM engineering_events
            WHERE tenant_id = :tenantId
            AND timestamp >= :since
            GROUP BY DATE(timestamp), source
            ORDER BY DATE(timestamp) ASC, source ASC
            """, nativeQuery = true)
    List<Object[]> getDailyEventCountBySource(
            @Param("tenantId") UUID tenantId,
            @Param("since") LocalDateTime since
    );

    // -------------------------------------------------------
    // Intelligence Layer — Jira Cycle Time
    // Finds pairs of TICKET_CREATED and TICKET_UPDATED (status=Done) events
    // for the same ticket key (stored in branchName field) within a project
    // -------------------------------------------------------

    @Query("""
            SELECT e.branchName, MIN(e.timestamp), MAX(e.timestamp)
            FROM EngineeringEvent e
            WHERE e.tenantId = :tenantId
            AND e.source = 'JIRA'
            AND e.repositoryName = :project
            AND e.branchName IS NOT NULL
            GROUP BY e.branchName
            HAVING COUNT(e) > 1
            ORDER BY MIN(e.timestamp) DESC
            """)
    List<Object[]> getJiraTicketTimestampPairs(
            @Param("tenantId") UUID tenantId,
            @Param("project") String project
    );

    // -------------------------------------------------------
    // Intelligence Layer — Events within a time window (any source)
    // Used for sprint velocity and trend analysis
    // -------------------------------------------------------

    @Query("""
            SELECT e FROM EngineeringEvent e
            WHERE e.tenantId = :tenantId
            AND e.source = :source
            AND e.timestamp BETWEEN :from AND :to
            ORDER BY e.timestamp DESC
            """)
    List<EngineeringEvent> findBySourceAndTimeWindow(
            @Param("tenantId") UUID tenantId,
            @Param("source") String source,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // -------------------------------------------------------
    // Utility — find events by source (all time)
    // -------------------------------------------------------

    @Query("""
            SELECT e FROM EngineeringEvent e
            WHERE e.tenantId = :tenantId
            AND e.source = :source
            ORDER BY e.timestamp DESC
            """)
    List<EngineeringEvent> findBySource(
            @Param("tenantId") UUID tenantId,
            @Param("source") String source
    );
}

