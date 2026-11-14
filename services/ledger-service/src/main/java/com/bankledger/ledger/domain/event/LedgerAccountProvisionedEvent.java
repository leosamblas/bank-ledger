package com.bankledger.ledger.domain.event;

import com.bankledger.commons.constant.KafkaTopics;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain event published to transactional outbox when a ledger account is provisioned.
 * Canonical topic: ledger.account-events.v1
 * Event type: LedgerAccountProvisioned
 */
public record LedgerAccountProvisionedEvent(
        UUID accountId,
        String code,
        Instant provisionedAt
) implements Serializable {

    public static final String TOPIC = KafkaTopics.LEDGER_ACCOUNT_EVENTS_V1;
    public static final String EVENT_TYPE = "LedgerAccountProvisioned";

    public LedgerAccountProvisionedEvent {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(provisionedAt, "provisionedAt must not be null");
    }
}
