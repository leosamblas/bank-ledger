package com.bankledger.ledger.domain.event;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.ledger.JournalRejectedPayload;
import com.bankledger.ledger.domain.model.JournalStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain event emitted when a journal transaction is rejected (e.g. INSUFFICIENT_FUNDS).
 * Canonical topic: ledger.journal-results.v1
 * Event type: JournalRejected
 */
public record JournalRejectedEvent(
        UUID transactionId,
        String type,
        JournalStatus status,
        String rejectCode,
        String rejectReason,
        UUID correlationId,
        Instant rejectedAt
) implements Serializable {

    public static final String TOPIC = KafkaTopics.LEDGER_JOURNAL_RESULTS_V1;
    public static final String EVENT_TYPE = "JournalRejected";

    public JournalRejectedEvent {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(rejectCode, "rejectCode must not be null");
        Objects.requireNonNull(rejectReason, "rejectReason must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(rejectedAt, "rejectedAt must not be null");
    }

    public JournalRejectedPayload toPayload() {
        return new JournalRejectedPayload(
                transactionId,
                type,
                status.name(),
                rejectCode,
                rejectReason,
                correlationId,
                rejectedAt
        );
    }
}
