package com.ai.engine.backend.security;

import com.ai.engine.backend.context.TenantContext;
import com.ai.engine.backend.service.EncryptionService;
import com.ai.engine.backend.service.TenantCredentialService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

/**
 * Servlet Filter that intercepts /webhooks/* requests and verifies the webhook payload
 * signatures using HMAC-SHA256.
 *
 * Per-Tenant Support:
 *   - Webhooks must include ?tenantId=<uuid> query parameter
 *   - Signature is verified using the per-tenant secret from the database
 *   - Falls back to static config for backward compatibility
 *
 * Supported:
 *   - GitHub webhooks: expects signature in 'X-Hub-Signature-256' header
 *   - Jira webhooks: expects signature in 'X-Hub-Signature' header
 */
@Component
@Slf4j
public class WebhookSecurityFilter implements Filter {

    @Value("${webhook.secret.github:}")
    private String githubSecretStatic;

    @Value("${webhook.secret.jira:}")
    private String jiraSecretStatic;

    @Autowired(required = false)
    private TenantCredentialService credentialService;

    @Autowired(required = false)
    private EncryptionService encryptionService;

    @Autowired(required = false)
    private TenantContext tenantContext;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Only intercept webhook endpoints
        if (!path.startsWith("/webhooks")) {
            chain.doFilter(request, response);
            return;
        }

        // Cache the request body so it can be read multiple times (for validation then parsing)
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(httpRequest);

        // Extract tenantId from query parameter: /webhooks/github?tenantId=<uuid>
        String tenantIdParam = httpRequest.getParameter("tenantId");
        
        if (tenantIdParam != null && tenantContext != null) {
            try {
                UUID tenantId = UUID.fromString(tenantIdParam);
                tenantContext.setCurrentTenantId(tenantId);
                log.debug("Set TenantContext to: {}", tenantId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenantId parameter: {}", tenantIdParam);
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid tenantId");
                return;
            }
        }

        if (path.startsWith("/webhooks/github")) {
            String signatureHeader = httpRequest.getHeader("X-Hub-Signature-256");
            String secret = getGitHubWebhookSecret();
            
            if (secret != null && !secret.isBlank()) {
                if (signatureHeader == null || !verifySignature(wrappedRequest.getCachedBody(), secret, signatureHeader)) {
                    log.warn("Unauthorized GitHub webhook access: signature mismatch or missing. Tenant: {}", tenantIdParam);
                    httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid signature");
                    return;
                }
                log.debug("GitHub webhook signature verified for tenant: {}", tenantIdParam);
            } else {
                log.debug("GitHub webhook secret is not configured; skipping signature verification for tenant: {}", tenantIdParam);
            }
        } else if (path.startsWith("/webhooks/jira")) {
            String signatureHeader = httpRequest.getHeader("X-Hub-Signature");
            String secret = getJiraWebhookSecret();
            
            if (secret != null && !secret.isBlank()) {
                if (signatureHeader == null || !verifySignature(wrappedRequest.getCachedBody(), secret, signatureHeader)) {
                    log.warn("Unauthorized Jira webhook access: signature mismatch or missing. Tenant: {}", tenantIdParam);
                    httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid signature");
                    return;
                }
                log.debug("Jira webhook signature verified for tenant: {}", tenantIdParam);
            } else {
                log.debug("Jira webhook secret is not configured; skipping signature verification for tenant: {}", tenantIdParam);
            }
        }

        chain.doFilter(wrappedRequest, response);
    }

    /**
     * Get GitHub webhook secret for the current tenant.
     * Tries per-tenant database first, then falls back to static config.
     */
    private String getGitHubWebhookSecret() {
        // Try per-tenant database secret
        if (tenantContext != null && tenantContext.hasTenant() && credentialService != null) {
            try {
                Optional<String> secret = credentialService.getGitHubWebhookSecret();
                if (secret.isPresent()) {
                    return secret.get();
                }
            } catch (Exception e) {
                log.debug("Failed to load GitHub webhook secret from database: {}", e.getMessage());
            }
        }
        
        // Fall back to static config
        return githubSecretStatic;
    }

    /**
     * Get Jira webhook secret for the current tenant.
     * Tries per-tenant database first, then falls back to static config.
     */
    private String getJiraWebhookSecret() {
        // Try per-tenant database secret
        if (tenantContext != null && tenantContext.hasTenant() && credentialService != null) {
            try {
                Optional<String> secret = credentialService.get("JIRA", "webhook_secret");
                if (secret.isPresent()) {
                    return secret.get();
                }
            } catch (Exception e) {
                log.debug("Failed to load Jira webhook secret from database: {}", e.getMessage());
            }
        }
        
        // Fall back to static config
        return jiraSecretStatic;
    }

    /**
     * Verifies the HMAC-SHA256 signature.
     * Format expected: sha256=<hex_encoded_hash>
     */
    private boolean verifySignature(byte[] payload, String secret, String signatureHeader) {
        try {
            if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
                return false;
            }
            String receivedSignature = signatureHeader.substring("sha256=".length());

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] rawHmac = mac.doFinal(payload);

            // Convert calculated HMAC to Hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String calculatedSignature = hexString.toString();

            // Timing-safe comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify webhook signature: {}", e.getMessage(), e);
            return false;
        }
    }
}
