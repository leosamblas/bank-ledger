package com.bankledger.auth.domain.event;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
import com.bankledger.commons.events.v1.account.AccountReadyPayload;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain record representing the AccountReady domain event.
 * Published to topic 'account.ready.v1' wrapped in KafkaEventEnvelope<AccountReadyPayload>.
 */
public record AccountReadyEvent(
        UUID eventId,
        AccountReadyPayload payload,
        Instant occurredAt
) implements Serializable {

    public static final String EVENT_TYPE = "AccountReady";
    public static final String TOPIC = KafkaTopics.ACCOUNT_READY_V1;

    public AccountReadyEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AccountReadyEvent of(AccountReadyPayload payload, Instant now) {
        return new AccountReadyEvent(UUID.randomUUID(), payload, now);
    }

    public KafkaEventEnvelope<AccountReadyPayload> toEnvelope() {
        return new KafkaEventEnvelope<>(
                eventId,
                eventId,
                "auth-service",
                EVENT_TYPE,
                "application/json",
                "1.0",
                occurredAt,
                payload
        );
    }
}
