package com.bankledger.ledger.adapters.in.rest.dto;

/**
 * REST response DTO for double-entry trial balance.
 */
public record TrialBalanceResponse(
        long debits,
        long credits,
        long difference
) {
}
