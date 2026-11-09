package com.bankledger.account.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
