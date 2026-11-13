package com.bankledger.ledger.adapters.in.rest.dto;

import java.util.UUID;

/**
 * REST response DTO for consistent account balance.
 */
public record LedgerBalanceResponse(
        UUID accountId,
        long balance,
        long lastSequence,
        String status
) {
}
