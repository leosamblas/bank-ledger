package com.bankledger.transaction.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Inbound command representing a deposit request.
 */
public record CreateDepositCommand(
        UUID destinationAccountId,
        long amount,
        String description,
        String terminalId,
        UUID requestedBy,
        UUID correlationId
) {
    public CreateDepositCommand {
        Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero, was: " + amount);
        }
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
    }
}
