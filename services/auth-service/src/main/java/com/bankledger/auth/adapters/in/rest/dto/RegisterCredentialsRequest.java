package com.bankledger.auth.adapters.in.rest.dto;

import com.bankledger.commons.security.dto.RegisterCredentialsPayload;
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
) {
    public RegisterCredentialsRequest(RegisterCredentialsPayload payload) {
        this(
                payload.customerId(),
                payload.cpf(),
                payload.password(),
                payload.accountId(),
                payload.branch(),
                payload.accountNumber(),
                payload.fullName(),
                payload.email()
        );
    }

    public RegisterCredentialsPayload toPayload() {
        return new RegisterCredentialsPayload(
                customerId,
                cpf,
                password,
                accountId,
                branch,
                accountNumber,
                fullName,
                email
        );
    }

    public static RegisterCredentialsRequest fromPayload(RegisterCredentialsPayload payload) {
        return new RegisterCredentialsRequest(payload);
    }
}
