package com.bankledger.auth.application.port.in;

import java.util.UUID;

public record LoginResult(
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
