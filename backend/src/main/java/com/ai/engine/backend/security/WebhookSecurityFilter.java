package com.ai.engine.backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Servlet Filter that intercepts /webhooks/* requests and verifies the webhook payload
 * signatures using HMAC-SHA256.
 *
 * Supported:
 *   - GitHub webhooks: expects signature in 'X-Hub-Signature-256' header
 *   - Jira webhooks: expects signature in 'X-Hub-Signature' header
 */
@Component
public class WebhookSecurityFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecurityFilter.class);

    @Value("${webhook.secret.github:}")
    private String githubSecret;

    @Value("${webhook.secret.jira:}")
    private String jiraSecret;

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

        if (path.startsWith("/webhooks/github")) {
            String signatureHeader = httpRequest.getHeader("X-Hub-Signature-256");
            if (githubSecret != null && !githubSecret.isBlank()) {
                if (signatureHeader == null || !verifySignature(wrappedRequest.getCachedBody(), githubSecret, signatureHeader)) {
                    log.warn("Unauthorized GitHub webhook access: signature mismatch or missing. Header: {}", signatureHeader);
                    httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid signature");
                    return;
                }
            } else {
                log.debug("GitHub webhook secret is not configured; skipping signature verification.");
            }
        } else if (path.startsWith("/webhooks/jira")) {
            String signatureHeader = httpRequest.getHeader("X-Hub-Signature");
            if (jiraSecret != null && !jiraSecret.isBlank()) {
                if (signatureHeader == null || !verifySignature(wrappedRequest.getCachedBody(), jiraSecret, signatureHeader)) {
                    log.warn("Unauthorized Jira webhook access: signature mismatch or missing. Header: {}", signatureHeader);
                    httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid signature");
                    return;
                }
            } else {
                log.debug("Jira webhook secret is not configured; skipping signature verification.");
            }
        }

        chain.doFilter(wrappedRequest, response);
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
