package com.ai.engine.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(
    name = "user_platform_identities",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "platform", "platform_user_id"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPlatformIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ← Tenant scoping: which company/account owns this identity mapping
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String platform; // e.g., GITHUB, JIRA, SLACK

    @Column(name = "platform_user_id", nullable = false)
    private String platformUserId; // e.g., git username, slack user ID, jira email/id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private Engineer engineer;
}
