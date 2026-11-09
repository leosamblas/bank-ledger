package com.bankledger.commons.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a transactional outbox event stored in table {@code outbox_event}.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    public static final String DEFAULT_HEADERS = "{}";
    public static final int DEFAULT_EVENT_VERSION = 1;

    @Id
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

    public OutboxEvent() {
    }

    public OutboxEvent(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            String topic,
            String messageKey,
            String payload,
            String headers,
            Instant createdAt,
            Instant publishedAt,
            int attempts
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion > 0 ? eventVersion : DEFAULT_EVENT_VERSION;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.headers = headers != null ? headers : DEFAULT_HEADERS;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.publishedAt = publishedAt;
        this.attempts = attempts >= 0 ? attempts : 0;
    }

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
                eventVersion,
                topic,
                messageKey,
                payload,
                headers,
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

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(int eventVersion) {
        this.eventVersion = eventVersion;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutboxEvent that = (OutboxEvent) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "id=" + id +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateId=" + aggregateId +
                ", eventType='" + eventType + '\'' +
                ", eventVersion=" + eventVersion +
                ", topic='" + topic + '\'' +
                ", messageKey='" + messageKey + '\'' +
                ", createdAt=" + createdAt +
                ", publishedAt=" + publishedAt +
                ", attempts=" + attempts +
                '}';
    }
}
