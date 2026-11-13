package com.bankledger.ledger.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain record representing an individual entry leg in a double-entry journal.
 */
public record Entry(
        UUID id,
        UUID journalId,
        UUID ledgerAccountId,
        EntryDirection direction,
        long amount,
        long balanceAfter,
        long sequence,
        Instant createdAt
) implements Serializable {

    public Entry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(journalId, "journalId must not be null");
        Objects.requireNonNull(ledgerAccountId, "ledgerAccountId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("Entry amount must be greater than zero, was: " + amount);
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("Entry sequence must be positive, was: " + sequence);
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
