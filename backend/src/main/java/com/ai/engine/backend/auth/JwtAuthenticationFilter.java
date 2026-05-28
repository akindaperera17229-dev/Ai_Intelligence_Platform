package com.ai.engine.backend.auth;

import com.ai.engine.backend.context.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JwtAuthenticationFilter: runs on every HTTP request to validate JWT tokens.
 * 
 * For requests with a valid JWT:
 * 1. Extracts and validates the token
 * 2. Populates SecurityContext with the authenticated principal
 * 3. Sets TenantContext so services know which tenant is active
 * 
 * This filter is stateless — it doesn't use sessions or cookies.
 * The JWT itself contains all the necessary identity and authorization info.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TenantContext tenantContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            // Extract JWT from "Authorization: Bearer <token>" header
            String token = extractBearerToken(request);

            if (token != null) {
                // Validate token and extract claims
                Claims claims = jwtTokenProvider.validateAndExtractClaims(token);

                // ← KEY: Set TenantContext so all downstream services can call
                // tenantContext.getCurrentTenantId() without passing it as a parameter
                tenantContext.setCurrentTenantId(
                    UUID.fromString(claims.get("tenantId", String.class))
                );

                // Tell Spring Security this request is authenticated
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), // user ID as principal
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("role")))
                    );
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.trace("JWT validated for user: {}, tenant: {}",
                    claims.getSubject(), claims.get("tenantId"));
            }
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            // Invalid/expired token — request will fail at @Secured endpoints
            // Unauthenticated requests are passed through (permitAll endpoints)
        } catch (Exception e) {
            log.error("Unexpected error in JWT authentication filter", e);
        }

        // Continue with the rest of the filter chain
        // (either authenticated or unauthenticated depending on the JWT)
        chain.doFilter(request, response);
    }

    /**
     * Extract JWT from "Authorization: Bearer <token>" header.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }

    /**
     * Skip filter for static resources and public endpoints
     * (still process all endpoints by default).
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // Don't filter static resources
        return path.startsWith("/static/") || path.startsWith("/public/");
    }
}
