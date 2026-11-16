package com.bankledger.auth.adapters.in.rest.dto;

import java.util.UUID;

public record TokenResponse(
        String token,
        Long expiresIn,
        String tokenType,
        String refreshToken,
        UserInfo user
) {
    public TokenResponse(String token, Long expiresIn, String tokenType, UserInfo user) {
        this(token, expiresIn, tokenType, null, user);
    }

    public record UserInfo(
            UUID id,
            String fullName,
            String cpf,
            String email
    ) {}
}
