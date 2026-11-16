package com.bankledger.commons.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Standard inbound Kafka event envelope for CloudEvents 1.0 JSON payloads.
 * Deserialized directly via {@link tools.jackson.databind.ObjectMapper} without JsonNode.
 *
 * @param <T> typed event payload
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KafkaEventEnvelope<T>(
        UUID id,
        UUID eventId,
        String source,
        String type,
        String datacontenttype,
        String specversion,
        Instant time,
        T data
) implements Serializable {

    /**
     * Resolves the effective event ID from either {@code id} or {@code eventId}.
     */
    public UUID resolveEventId() {
        return id != null ? id : eventId;
    }
}
