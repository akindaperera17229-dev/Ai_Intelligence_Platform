package com.ai.engine.backend.normalization;

import com.ai.engine.backend.model.EngineeringEvent;

public interface EventNormalizer {
    /**
     * Determines whether this normalizer supports the given platform source and event type.
     */
    boolean supports(String source, String eventType);

    /**
     * Normalizes the raw JSON payload into an EngineeringEvent.
     */
    EngineeringEvent normalize(String rawPayload);
}
