package com.bankledger.account.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "cpf must not be blank")
        String cpf,

        @NotBlank(message = "password must not be blank")
        String password
) {
}

