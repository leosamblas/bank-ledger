package com.bankledger.commons.events.v1.ledger;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical event payload emitted when a journal transaction is rejected (v1).
 * Zero framework dependencies (pure Java standard library).
 */
public record JournalRejectedPayload(
        UUID transactionId,
        String type,
        String status,
        String rejectCode,
        String rejectReason,
        UUID correlationId,
        Instant rejectedAt
) implements Serializable {

    public JournalRejectedPayload {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(rejectCode, "rejectCode must not be null");
        Objects.requireNonNull(rejectReason, "rejectReason must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(rejectedAt, "rejectedAt must not be null");
    }
}
