package com.bankledger.transaction.domain.event;

import com.bankledger.commons.events.v1.ledger.JournalPostedPayload;
import com.bankledger.commons.events.v1.ledger.JournalRejectedPayload;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain record representing the incoming result of a journal operation (posted or rejected).
 * Wraps topic ledger.journal-results.v1 payloads.
 */
public record JournalResultPayload(
        UUID journalId,
        UUID transactionId,
        String type,
        String status,
        Long totalAmount,
        String currency,
        String rejectCode,
        String rejectReason,
        UUID correlationId,
        Instant timestamp
) implements Serializable {

    public JournalResultPayload {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static JournalResultPayload fromPosted(JournalPostedPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return new JournalResultPayload(
                payload.journalId(),
                payload.transactionId(),
                payload.type(),
                payload.status(),
                payload.totalAmount(),
                payload.currency(),
                null,
                null,
                payload.correlationId(),
                payload.postedAt()
        );
    }

    public static JournalResultPayload fromRejected(JournalRejectedPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return new JournalResultPayload(
                null,
                payload.transactionId(),
                payload.type(),
                payload.status(),
                null,
                null,
                payload.rejectCode(),
                payload.rejectReason(),
                payload.correlationId(),
                payload.rejectedAt()
        );
    }
}
