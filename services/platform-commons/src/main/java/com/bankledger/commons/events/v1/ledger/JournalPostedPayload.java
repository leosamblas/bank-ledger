package com.bankledger.commons.events.v1.ledger;

import com.bankledger.commons.constant.BankingConstants;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical event payload emitted when a journal transaction is successfully posted (v1).
 * Zero framework dependencies (pure Java standard library).
 */
public record JournalPostedPayload(
        UUID journalId,
        UUID transactionId,
        String type,
        String status,
        long totalAmount,
        String currency,
        UUID correlationId,
        Instant postedAt
) implements Serializable {

    public JournalPostedPayload {
        Objects.requireNonNull(journalId, "journalId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (totalAmount < 0) {
            throw new IllegalArgumentException("totalAmount must not be negative, was: " + totalAmount);
        }
        if (currency == null || currency.isBlank()) {
            currency = BankingConstants.DEFAULT_CURRENCY;
        }
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(postedAt, "postedAt must not be null");
    }
}
