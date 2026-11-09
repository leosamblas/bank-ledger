package com.bankledger.commons.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing an idempotent inbox record stored in table {@code inbox_event}.
 * Uses composite primary key (eventId, handler) to guarantee exactly-once processing per consumer.
 */
@Entity
@Table(name = "inbox_event")
@IdClass(InboxEventId.class)
public class InboxEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Id
    @Column(name = "handler", nullable = false, length = 80)
    private String handler;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    public InboxEvent() {
    }

    public InboxEvent(UUID eventId, String handler, Instant processedAt) {
        this.eventId = eventId;
        this.handler = handler;
        this.processedAt = processedAt != null ? processedAt : Instant.now();
    }

    public InboxEvent(UUID eventId, String handler) {
        this(eventId, handler, Instant.now());
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InboxEvent that = (InboxEvent) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(handler, that.handler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, handler);
    }

    @Override
    public String toString() {
        return "InboxEvent{" +
                "eventId=" + eventId +
                ", handler='" + handler + '\'' +
                ", processedAt=" + processedAt +
                '}';
    }
}
