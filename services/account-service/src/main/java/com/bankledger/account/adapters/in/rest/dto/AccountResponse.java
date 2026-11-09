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
        Instant openedAt
) {
}

