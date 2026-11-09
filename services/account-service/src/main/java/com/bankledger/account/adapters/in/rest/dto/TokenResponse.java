package com.bankledger.account.adapters.in.rest.dto;

public record TokenResponse(
        String token,
        Long expiresIn
) {
}

