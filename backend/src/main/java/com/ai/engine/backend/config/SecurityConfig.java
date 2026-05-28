package com.ai.engine.backend.config;

import com.ai.engine.backend.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig: Spring Security configuration for a stateless JWT-based API.
 * 
 * Key features:
 * - CSRF disabled (stateless REST API, not a traditional web app)
 * - Session management: STATELESS (no session cookies)
 * - CORS configured for frontend (localhost:3000)
 * - JWT filter added to validate tokens on every request
 * - Public endpoints: /api/auth/**, /webhooks/**
 * - Protected endpoints: everything else (requires valid JWT)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Main security filter chain configuration.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // Disable CSRF (stateless API doesn't need it)
            .csrf(csrf -> csrf.disable())

            // No sessions — each request must include a valid JWT
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Enable CORS for frontend
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public: GitHub OAuth flow
                .requestMatchers(HttpMethod.GET, "/api/auth/**").permitAll()

                // Public: webhook receivers (they have their own signature verification)
                .requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // Add JWT filter before the standard auth filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            .build();
    }

    /**
     * CORS configuration for the frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow requests from the React frontend
        config.setAllowedOrigins(List.of(
            "http://localhost:3000",      // Local development
            "http://localhost:4200"       // Alternate dev port
        ));

        // Allow common HTTP methods
        config.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Allow any headers (Authorization, Content-Type, etc)
        config.setAllowedHeaders(List.of("*"));

        // Allow credentials (cookies, if used in future)
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        // Register configuration for all paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
