package com.bankledger.auth.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "CPF is required")
        String cpf,

        @NotBlank(message = "Password is required")
        String password
) {}
