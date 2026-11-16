package com.bankledger.auth.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record IntrospectRequest(
        @NotBlank(message = "Token is required")
        String token
) {}
