package com.bankledger.account.adapters.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID customerId,
        String agency,
        String number,
        String digit,
        String status,
        Instant openedAt,
        String holderName
) {
    public AccountResponse(
            UUID id,
            UUID customerId,
            String agency,
            String number,
            String digit,
            String status,
            Instant openedAt
    ) {
        this(id, customerId, agency, number, digit, status, openedAt, null);
    }
}
