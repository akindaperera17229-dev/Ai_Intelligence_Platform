package com.ai.engine.backend.controller;

import com.ai.engine.backend.auth.GitHubOAuthService;
import com.ai.engine.backend.auth.JwtTokenProvider;
import com.ai.engine.backend.dto.AuthResponse;
import com.ai.engine.backend.dto.UserInfoResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * AuthController: handles authentication and authorization endpoints.
 * 
 * - GET /api/auth/github/login-url → returns GitHub OAuth URL
 * - GET /api/auth/github/callback → GitHub redirects here after user approves
 * - GET /api/auth/me → returns current user info (requires JWT)
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@Slf4j
public class AuthController {

    @Autowired
    private GitHubOAuthService gitHubOAuthService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${github.client.id}")
    private String githubClientId;

    /**
     * GET /api/auth/github/login-url
     * 
     * Returns the GitHub OAuth authorization URL that the frontend should redirect to.
     * The frontend stores this URL and sends the user to GitHub's OAuth consent screen.
     */
    @GetMapping("/github/login-url")
    public ResponseEntity<Map<String, String>> getLoginUrl() {
        String state = UUID.randomUUID().toString(); // CSRF protection
        String url = "https://github.com/login/oauth/authorize"
            + "?client_id=" + githubClientId
            + "&scope=read:user,user:email"
            + "&state=" + state;

        log.debug("Generated GitHub OAuth login URL with state: {}", state);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * GET /api/auth/github/callback?code=...
     * 
     * GitHub redirects here after the user approves.
     * The `code` parameter is exchanged for an access token, user profile is fetched,
     * and a JWT is issued.
     */
    @GetMapping("/github/callback")
    public ResponseEntity<AuthResponse> callback(@RequestParam String code) {
        try {
            log.debug("GitHub OAuth callback received with code: {}", code.substring(0, 10) + "...");
            String jwt = gitHubOAuthService.handleCallback(code);
            return ResponseEntity.ok(new AuthResponse(jwt));
        } catch (Exception e) {
            log.error("OAuth callback processing failed", e);
            return ResponseEntity.status(401).build();
        }
    }

    /**
     * GET /api/auth/me
     * 
     * Returns information about the currently authenticated user (from the JWT).
     * Requires a valid JWT token in the Authorization header: "Bearer <token>"
     * 
     * This endpoint is used by the frontend after login to display user info
     * and verify the user is still authenticated.
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header in /api/auth/me");
                return ResponseEntity.status(401).build();
            }

            String token = authHeader.substring(7);
            Claims claims = jwtTokenProvider.validateAndExtractClaims(token);

            String userId = claims.getSubject();
            String tenantId = claims.get("tenantId", String.class);
            String githubLogin = claims.get("githubLogin", String.class);
            String role = claims.get("role", String.class);

            UserInfoResponse response = new UserInfoResponse(
                userId,
                tenantId,
                githubLogin,
                role,
                "" // email not stored in JWT for privacy
            );

            log.debug("Authenticated user: {} from tenant {}", githubLogin, tenantId);
            return ResponseEntity.ok(response);
        } catch (JwtException e) {
            log.warn("JWT validation failed in /api/auth/me: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Unexpected error in /api/auth/me", e);
            return ResponseEntity.status(500).build();
        }
    }
}
