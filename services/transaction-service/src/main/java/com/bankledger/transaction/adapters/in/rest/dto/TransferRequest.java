package com.bankledger.transaction.adapters.in.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record TransferRequest(
        @NotNull(message = "destinationAccountId must not be null")
        UUID destinationAccountId,

        @NotNull(message = "amount must not be null")
        @Positive(message = "amount must be greater than zero")
        Long amount,

        String description,

        String destinationHolderName,
        String destinationDocument,
        String destinationAccount
) {
    public TransferRequest(UUID destinationAccountId, Long amount, String description) {
        this(destinationAccountId, amount, description, null, null, null);
    }
}
