package com.bankledger.ledger.domain.event;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.ledger.JournalPostedPayload;
import com.bankledger.ledger.domain.model.JournalStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain event emitted when a journal transaction is successfully posted.
 * Canonical topic: ledger.journal-results.v1
 * Event type: JournalPosted
 */
public record JournalPostedEvent(
        UUID journalId,
        UUID transactionId,
        String type,
        JournalStatus status,
        long totalAmount,
        String currency,
        UUID correlationId,
        Instant postedAt
) implements Serializable {

    public static final String TOPIC = KafkaTopics.LEDGER_JOURNAL_RESULTS_V1;
    public static final String EVENT_TYPE = "JournalPosted";

    public JournalPostedEvent {
        Objects.requireNonNull(journalId, "journalId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(postedAt, "postedAt must not be null");
    }

    public JournalPostedPayload toPayload() {
        return new JournalPostedPayload(
                journalId,
                transactionId,
                type,
                status.name(),
                totalAmount,
                currency,
                correlationId,
                postedAt
        );
    }
}
