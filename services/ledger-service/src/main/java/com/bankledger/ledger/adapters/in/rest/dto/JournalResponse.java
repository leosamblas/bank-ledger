package com.bankledger.ledger.adapters.in.rest.dto;

import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for a journal and its entry legs.
 */
public record JournalResponse(
        UUID journalId,
        UUID transactionId,
        String type,
        String status,
        long totalAmount,
        List<JournalEntryResponse> entries
) {
}
