package com.bankledger.transaction.adapters.in.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WithdrawalRequest(
        @NotNull(message = "amount must not be null")
        @Positive(message = "amount must be greater than zero")
        Long amount,
        String description
) {
}
