package com.bankledger.auth.application.port.in;

import java.util.UUID;

public record IntrospectResult(
        boolean active,
        String sub,
        UUID customerId,
        UUID accountId,
        String branch,
        String accountNumber,
        String role,
        Long exp,
        Long iat,
        String iss
) {
    public static IntrospectResult inactive() {
        return new IntrospectResult(false, null, null, null, null, null, null, null, null, null);
    }
}
