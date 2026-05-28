package com.ai.engine.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TenantCredential: stores encrypted API tokens and secrets for a tenant.
 * Credentials are AES-256-GCM encrypted at rest and decrypted on-demand.
 *
 * Examples:
 * - platform=JIRA, key=base_url,   value=https://company.atlassian.net
 * - platform=JIRA, key=api_token,  value=<encrypted>
 * - platform=GITHUB, key=webhook_secret, value=<encrypted>
 */
@Entity
@Table(name = "tenant_credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String platform;       // JIRA / GITHUB / SLACK

    @Column(nullable = false)
    private String credentialKey;  // base_url / user_email / api_token / webhook_secret

    @Column(nullable = false, columnDefinition = "TEXT")
    private String credentialValue; // AES-256-GCM encrypted (Base64 encoded)

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
