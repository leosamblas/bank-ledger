package com.bankledger.commons.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an idempotent inbox record stored in table {@code inbox_event}.
 * Uses composite primary key (eventId, handler) to guarantee exactly-once processing per consumer.
 */
@Entity
@Table(name = "inbox_event")
@IdClass(InboxEventId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InboxEvent {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "handler", nullable = false, length = 80)
    private String handler;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    public InboxEvent(UUID eventId, String handler) {
        this(eventId, handler, Instant.now());
    }
}
