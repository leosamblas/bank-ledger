package com.bankledger.transaction.domain.event;

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
}
