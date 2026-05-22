package com.ai.engine.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "user_platform_identities",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"platform", "platformUserId"})
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

    @Column(nullable = false)
    private String platform; // e.g., GITHUB, JIRA, SLACK

    @Column(nullable = false)
    private String platformUserId; // e.g., git username, slack user ID, jira email/id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private Engineer engineer;
}
