package com.ai.engine.backend.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

/**
 * TenantContext: carries the current tenant ID through a single HTTP request.
 * 
 * Scope: @RequestScope — one instance created per HTTP request, automatically cleaned up
 * when the request completes. Thread-safe by design because each request is processed
 * in its own context.
 * 
 * Usage: Inject this into any service, and call getCurrentTenantId() to get the
 * tenant for the current request. No need to pass tenantId as a parameter.
 * 
 * Example:
 *     @Service
 *     public class MyService {
 *         @Autowired TenantContext tenantContext;
 *         
 *         public void doWork() {
 *             UUID tenantId = tenantContext.getCurrentTenantId();
 *             // use tenantId to scope queries
 *         }
 *     }
 */
@Component
@RequestScope
@Slf4j
public class TenantContext {

    private UUID currentTenantId;

    /**
     * Get the current tenant ID for this request.
     * @throws IllegalStateException if no tenant is set (unauthenticated request)
     */
    public UUID getCurrentTenantId() {
        if (currentTenantId == null) {
            throw new IllegalStateException(
                "No tenant context set. This endpoint requires authentication.");
        }
        return currentTenantId;
    }

    /**
     * Set the current tenant ID for this request.
     * Called by JwtAuthenticationFilter after validating the JWT.
     */
    public void setCurrentTenantId(UUID tenantId) {
        this.currentTenantId = tenantId;
        log.trace("TenantContext set to: {}", tenantId);
    }

    /**
     * Check if a tenant context has been established.
     */
    public boolean hasTenant() {
        return currentTenantId != null;
    }
}
