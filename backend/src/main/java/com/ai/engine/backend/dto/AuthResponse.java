package com.ai.engine.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from GitHub OAuth callback or token validation.
 * Contains the JWT token for subsequent authenticated requests.
 */
public record AuthResponse(
    @JsonProperty("token")
    String token
) {}
