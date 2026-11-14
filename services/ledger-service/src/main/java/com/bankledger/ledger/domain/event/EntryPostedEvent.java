package com.bankledger.ledger.domain.event;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.ledger.EntryPostedPayload;
import com.bankledger.ledger.domain.model.EntryDirection;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain event emitted for each entry leg posted to the ledger for audit and analytics.
 * Canonical topic: ledger.entries.v1
 * Event type: EntryPosted
 */
public record EntryPostedEvent(
        UUID entryId,
        UUID journalId,
        UUID transactionId,
        UUID accountId,
        EntryDirection direction,
        long amount,
        long balanceAfter,
        long sequence,
        Instant postedAt
) implements Serializable {

    public static final String TOPIC = KafkaTopics.LEDGER_ENTRIES_V1;
    public static final String EVENT_TYPE = "EntryPosted";

    public EntryPostedEvent {
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(journalId, "journalId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(postedAt, "postedAt must not be null");
    }

    public EntryPostedPayload toPayload() {
        return new EntryPostedPayload(
                entryId,
                journalId,
                accountId,
                direction.name(),
                amount,
                postedAt
        );
    }
}
