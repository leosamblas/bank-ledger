package com.bankledger.ledger.adapters.in.rest.dto;

import java.util.UUID;

/**
 * REST response DTO for an individual entry leg in a journal.
 */
public record JournalEntryResponse(
        UUID accountId,
        String direction,
        long amount,
        long balanceAfter,
        long sequence
) {
}
