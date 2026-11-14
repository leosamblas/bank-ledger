package com.bankledger.commons.events.v1.ledger;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical event payload emitted for an individual ledger entry leg posted to the ledger (v1).
 * Zero framework dependencies (pure Java standard library).
 */
public record EntryPostedPayload(
        UUID entryId,
        UUID journalId,
        UUID accountId,
        String entryType,
        long amount,
        Instant timestamp
) implements Serializable {

    public EntryPostedPayload {
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(journalId, "journalId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(entryType, "entryType must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero, was: " + amount);
        }
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
