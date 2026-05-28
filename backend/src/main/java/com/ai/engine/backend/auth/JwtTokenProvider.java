package com.ai.engine.backend.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtTokenProvider: creates and validates JWT tokens for authenticated requests.
 * 
 * Tokens contain:
 * - subject: TenantUser UUID
 * - tenantId: Tenant UUID
 * - githubLogin: GitHub username
 * - role: user role (ADMIN, etc)
 * - exp: expiration time
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    /**
     * Generate a signed JWT token for a TenantUser.
     */
    public String generateToken(com.ai.engine.backend.model.TenantUser user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("tenantId", user.getTenant().getId().toString())
            .claim("githubLogin", user.getGithubLogin())
            .claim("role", user.getRole())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
            .signWith(getSigningKey())
            .compact();
    }

    /**
     * Validate a JWT token and extract its claims.
     * @throws JwtException if token is invalid or expired
     */
    public Claims validateAndExtractClaims(String token) throws JwtException {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Get the signing key from the secret.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
