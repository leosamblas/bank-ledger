package com.bankledger.commons.domain.event;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain record representing a CloudEvents v1.0 compliant envelope.
 * Wraps arbitrary event payloads {@code T} with standard CloudEvents attributes
 * and tracing metadata.
 * Contains ZERO framework dependencies (no Spring, no Hibernate).
 *
 * @param <T> payload type
 */
public record EventEnvelope<T>(
        UUID id,
        String source,
        String type,
        String datacontenttype,
        String dataschema,
        Instant time,
        String specversion,
        EventMetadata metadata,
        T data
) {

    public static final String DEFAULT_SPEC_VERSION = "1.0";
    public static final String DEFAULT_DATA_CONTENT_TYPE = "application/json";

    public EventEnvelope {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(type, "type must not be null");

        if (id == null) {
            id = UUID.randomUUID();
        }
        if (datacontenttype == null) {
            datacontenttype = DEFAULT_DATA_CONTENT_TYPE;
        }
        if (specversion == null) {
            specversion = DEFAULT_SPEC_VERSION;
        }
        if (time == null) {
            time = Instant.now(Clock.systemUTC());
        }
        if (metadata == null) {
            metadata = EventMetadata.create(null, null, null);
        }
    }

    /**
     * Factory helper to create an envelope with default CloudEvents attributes and generated metadata if needed.
     */
    public static <T> EventEnvelope<T> create(String source, String type, T data, EventMetadata metadata) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                source,
                type,
                DEFAULT_DATA_CONTENT_TYPE,
                null,
                Instant.now(Clock.systemUTC()),
                DEFAULT_SPEC_VERSION,
                metadata != null ? metadata : EventMetadata.create(null, null, null),
                data
        );
    }

    /**
     * Factory helper to create an envelope with a schema URI and metadata.
     */
    public static <T> EventEnvelope<T> create(String source, String type, T data, String dataschema, EventMetadata metadata) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                source,
                type,
                DEFAULT_DATA_CONTENT_TYPE,
                dataschema,
                Instant.now(Clock.systemUTC()),
                DEFAULT_SPEC_VERSION,
                metadata != null ? metadata : EventMetadata.create(null, null, null),
                data
        );
    }

    /**
     * Factory helper to create an envelope with default metadata.
     */
    public static <T> EventEnvelope<T> create(String source, String type, T data) {
        return create(source, type, data, (EventMetadata) null);
    }
}
