package com.bankledger.commons.domain.event;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain record representing contextual metadata for an event.
 * Follows distributed tracing semantics: correlationId, causationId, and userId.
 * Contains ZERO framework dependencies (no Spring, no Hibernate).
 */
public record EventMetadata(
        UUID correlationId,
        UUID causationId,
        UUID userId,
        Instant timestamp
) {

    public EventMetadata {
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now(Clock.systemUTC());
        }
    }

    public static EventMetadata create(UUID correlationId, UUID causationId, UUID userId) {
        return new EventMetadata(
                correlationId != null ? correlationId : UUID.randomUUID(),
                causationId,
                userId,
                Instant.now(Clock.systemUTC())
        );
    }

    public static EventMetadata of(UUID correlationId, UUID causationId, UUID userId, Instant timestamp) {
        return new EventMetadata(correlationId, causationId, userId, timestamp);
    }
}
