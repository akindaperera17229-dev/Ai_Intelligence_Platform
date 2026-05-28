package com.ai.engine.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tenant: represents a single company or individual user account in the SaaS system.
 * One tenant = one isolated data silo (events, engineers, credentials all scoped to this tenant).
 */
@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;      // URL-friendly identifier: "techcorp"

    @Column(nullable = false)
    private String plan;      // FREE / PRO / ENTERPRISE

    @Column(unique = true)
    private String githubId;  // GitHub user/org numeric ID (immutable, from OAuth)

    private String avatarUrl; // GitHub avatar for UI

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
