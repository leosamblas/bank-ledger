package com.bankledger.auth.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegisterCredentialsRequest(
        @NotNull(message = "customerId is required")
        UUID customerId,

        @NotBlank(message = "cpf is required")
        String cpf,

        @NotBlank(message = "password is required")
        String password,

        UUID accountId,
        String branch,
        String accountNumber,
        String fullName,
        String email
) {}
