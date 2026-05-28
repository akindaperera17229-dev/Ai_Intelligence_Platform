package com.ai.engine.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebhookSecurityFilterTests {

    private WebhookSecurityFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new WebhookSecurityFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    void testDoFilter_NonWebhookPath_PassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/intelligence/workload");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(response);
    }

    @Test
    void testDoFilter_GithubWebhook_NoSecret_PassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/webhooks/github");
        ReflectionTestUtils.setField(filter, "githubSecretStatic", "");

        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        when(request.getInputStream()).thenReturn(new CachedBodyServletInputStream(body));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(CachedBodyHttpServletRequest.class), eq(response));
    }

    @Test
    void testDoFilter_GithubWebhook_ValidSignature_PassesThrough() throws Exception {
        String secret = "testsecret";
        String payload = "{\"ref\":\"refs/heads/main\"}";
        String signature = calculateHmacSha256(payload, secret);

        when(request.getRequestURI()).thenReturn("/webhooks/github");
        when(request.getHeader("X-Hub-Signature-256")).thenReturn("sha256=" + signature);
        ReflectionTestUtils.setField(filter, "githubSecretStatic", secret);

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        when(request.getInputStream()).thenReturn(new CachedBodyServletInputStream(body));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(CachedBodyHttpServletRequest.class), eq(response));
    }

    @Test
    void testDoFilter_GithubWebhook_InvalidSignature_Forbidden() throws Exception {
        String secret = "testsecret";
        String payload = "{\"ref\":\"refs/heads/main\"}";

        when(request.getRequestURI()).thenReturn("/webhooks/github");
        when(request.getHeader("X-Hub-Signature-256")).thenReturn("sha256=invalid-signature");
        ReflectionTestUtils.setField(filter, "githubSecretStatic", secret);

        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        when(request.getInputStream()).thenReturn(new CachedBodyServletInputStream(body));

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid signature");
        verifyNoInteractions(chain);
    }

    private String calculateHmacSha256(String data, String key) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
