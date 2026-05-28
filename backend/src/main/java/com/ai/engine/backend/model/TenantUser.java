package com.ai.engine.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TenantUser: represents a single login account (admin) within a tenant.
 * Current model: one admin per tenant (no invite system yet).
 */
@Entity
@Table(name = "tenant_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(unique = true, nullable = false)
    private String githubId;      // GitHub numeric user ID (immutable, from OAuth)

    @Column(nullable = false)
    private String githubLogin;   // GitHub username (can change, display only)

    private String email;

    private String avatarUrl;     // GitHub avatar for UI

    @Column(nullable = false)
    private String role;          // ADMIN (more roles to come)

    @Column(nullable = false)
    private boolean isActive;

    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
