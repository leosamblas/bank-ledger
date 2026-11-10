package com.bankledger.commons.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    @DisplayName("should create pending OutboxEvent with default values")
    void shouldCreatePendingEventWithDefaults() {
        UUID aggregateId = UUID.randomUUID();
        String payload = "{\"accountId\":\"" + aggregateId + "\"}";

        OutboxEvent event = OutboxEvent.pending(
                "Account",
                aggregateId,
                "AccountOpened",
                "account.events",
                aggregateId.toString(),
                payload
        );

        assertThat(event.getId()).isNotNull();
        assertThat(event.getAggregateType()).isEqualTo("Account");
        assertThat(event.getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getEventType()).isEqualTo("AccountOpened");
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getTopic()).isEqualTo("account.events");
        assertThat(event.getMessageKey()).isEqualTo(aggregateId.toString());
        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(event.getHeaders()).isEqualTo("{}");
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(0);
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    @DisplayName("should mark event as published")
    void shouldMarkPublished() {
        OutboxEvent event = OutboxEvent.pending(
                "Account",
                UUID.randomUUID(),
                "AccountOpened",
                "account.events",
                "key-1",
                "{}"
        );

        Instant publishTime = Instant.parse("2026-11-09T20:00:00Z");
        event.markPublished(publishTime);

        assertThat(event.getPublishedAt()).isEqualTo(publishTime);
        assertThat(event.isPublished()).isTrue();
    }

    @Test
    @DisplayName("should mark event as failed and increment attempts counter")
    void shouldMarkFailed() {
        OutboxEvent event = OutboxEvent.pending(
                "Account",
                UUID.randomUUID(),
                "AccountOpened",
                "account.events",
                "key-1",
                "{}"
        );

        assertThat(event.getAttempts()).isEqualTo(0);
        event.markFailed();
        assertThat(event.getAttempts()).isEqualTo(1);
        event.markFailed();
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("should check equality based on entity ID")
    void shouldCheckEqualityBasedOnId() {
        UUID id = UUID.randomUUID();
        OutboxEvent e1 = new OutboxEvent();
        e1.setId(id);

        OutboxEvent e2 = new OutboxEvent();
        e2.setId(id);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }
}
