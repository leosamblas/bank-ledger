package com.bankledger.account.adapters.in.rest.dto;

/**
 * Request body for secure POST /api/v1/accounts/lookup.
 * Prevents exposing sensitive PII (CPF/account numbers) in query parameters or access logs.
 */
public record AccountLookupRequest(
        String searchType,
        String cpf,
        String agency,
        String accountNumber
) {
}
