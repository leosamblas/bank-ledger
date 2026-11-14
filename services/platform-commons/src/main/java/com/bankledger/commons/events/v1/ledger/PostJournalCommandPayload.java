package com.bankledger.commons.events.v1.ledger;

import com.bankledger.commons.constant.BankingConstants;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical command payload requesting double-entry journal posting (v1).
 * Zero framework dependencies (pure Java standard library).
 */
public record PostJournalCommandPayload(
        UUID commandId,
        UUID transactionId,
        String type,
        long amount,
        String currency,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String terminalId,
        UUID correlationId,
        Instant timestamp
) implements Serializable {

    public PostJournalCommandPayload {
        if (commandId == null) {
            commandId = UUID.randomUUID();
        }
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero, was: " + amount);
        }
        if (currency == null || currency.isBlank()) {
            currency = BankingConstants.DEFAULT_CURRENCY;
        }
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
