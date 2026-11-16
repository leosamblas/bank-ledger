package com.bankledger.auth.adapters.in.rest.dto;

import java.util.UUID;

public record IntrospectResponse(
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
) {}
