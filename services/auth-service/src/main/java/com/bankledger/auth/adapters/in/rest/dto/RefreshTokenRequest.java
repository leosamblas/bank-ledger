package com.bankledger.auth.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken é obrigatório")
        String refreshToken
) {}
