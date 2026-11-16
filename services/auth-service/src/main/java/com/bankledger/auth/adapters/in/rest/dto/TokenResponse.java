package com.bankledger.auth.adapters.in.rest.dto;

import java.util.UUID;

public record TokenResponse(
        String token,
        Long expiresIn,
        String tokenType,
        String refreshToken,
        UserInfo user
) {
    public record UserInfo(
            UUID id,
            String fullName,
            String cpf,
            String email
    ) {}
}
