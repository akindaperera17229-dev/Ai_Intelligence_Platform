package com.ai.engine.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Authenticated user information extracted from the current JWT.
 */
public record UserInfoResponse(
    @JsonProperty("userId")
    String userId,

    @JsonProperty("tenantId")
    String tenantId,

    @JsonProperty("githubLogin")
    String githubLogin,

    @JsonProperty("role")
    String role,

    @JsonProperty("email")
    String email
) {}
