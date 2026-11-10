package com.bankledger.commons.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a transactional outbox event stored in table {@code outbox_event}.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxEvent {

    public static final String DEFAULT_HEADERS = "{}";
    public static final int DEFAULT_EVENT_VERSION = 1;

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion = DEFAULT_EVENT_VERSION;

    @Column(name = "topic", nullable = false, length = 120)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 64)
    private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", nullable = false, columnDefinition = "jsonb")
    private String headers = DEFAULT_HEADERS;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    /**
     * Factory method to create a pending outbox event with full parameters.
     */
    public static OutboxEvent pending(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            String topic,
            String messageKey,
            String payload,
            String headers
    ) {
        return new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                eventVersion > 0 ? eventVersion : DEFAULT_EVENT_VERSION,
                topic,
                messageKey,
                payload,
                headers != null ? headers : DEFAULT_HEADERS,
                Instant.now(),
                null,
                0
        );
    }

    /**
     * Factory method to create a pending outbox event with default version and empty headers.
     */
    public static OutboxEvent pending(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String topic,
            String messageKey,
            String payload
    ) {
        return pending(
                aggregateType,
                aggregateId,
                eventType,
                DEFAULT_EVENT_VERSION,
                topic,
                messageKey,
                payload,
                DEFAULT_HEADERS
        );
    }

    /**
     * Marks this event as published.
     *
     * @param when the instant when the event was published, or current instant if null
     */
    public void markPublished(Instant when) {
        this.publishedAt = when != null ? when : Instant.now();
    }

    /**
     * Increments the delivery attempt counter following a failure.
     */
    public void markFailed() {
        this.attempts++;
    }

    /**
     * Returns true if this event has been published.
     */
    public boolean isPublished() {
        return this.publishedAt != null;
    }
}
