package com.bankledger.transaction.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Inbound command representing a transfer request between two accounts.
 */
public record CreateTransferCommand(
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amount,
        String description,
        String terminalId,
        UUID requestedBy,
        UUID correlationId,
        String destinationHolderName,
        String destinationDocument,
        String destinationAccount
) {
    public CreateTransferCommand(
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amount,
            String description,
            String terminalId,
            UUID requestedBy,
            UUID correlationId
    ) {
        this(sourceAccountId, destinationAccountId, amount, description, terminalId, requestedBy, correlationId, null, null, null);
    }
    public CreateTransferCommand {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("sourceAccountId and destinationAccountId must be different");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero, was: " + amount);
        }
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
    }
}
