package com.ai.engine.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "engineering_events")

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter

public class EngineeringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ← Tenant scoping: which company/account owns this event
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /*
     * Normalized event type
     * Examples:
     * CODE_PUSH
     * PR_OPENED
     * TICKET_CREATED
     * MESSAGE_SENT
     */
    private String eventType;
    /*
     * Source platform
     * GITHUB / JIRA / SLACK
     */
    private String source;
    /*
     * Unified engineer identity
     */
    private String userId;
    private String engineerName;

    /*
     * Resolved Engineer profile
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id")
    private Engineer engineer;

    /*
     * Repository/project info
     */
    private String repositoryName;

    private String branchName;

    /*
     * Metrics
     */
    private Integer commitCount;

    /*
     * Event timestamp
     */
    private LocalDateTime timestamp;

    /*
     * Short human-readable summary
     */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /*
     * Platform-specific JSON metadata
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /*
     * Full raw payload
     */
    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    /*
     * Webhook/API/manual
     */
    private String ingestionSource;

   
}

