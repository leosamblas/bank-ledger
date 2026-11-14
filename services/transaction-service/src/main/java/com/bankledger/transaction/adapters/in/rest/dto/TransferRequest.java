package com.bankledger.transaction.adapters.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record TransferRequest(
        @NotNull(message = "destinationAccountId must not be null")
        UUID destinationAccountId,

        @NotNull(message = "amount must not be null")
        @Positive(message = "amount must be greater than zero")
        @Max(value = 5_000_000L, message = "amount must not exceed 5000000 cents (R$ 50.000,00)")
        Long amount,

        String description
) {
}
