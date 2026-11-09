package com.bankledger.account.application.port.out;

import java.util.UUID;

/**
 * Parsed claims from a verified security token.
 */
public record TokenClaims(
        UUID customerId,
        UUID accountId,
        String branch,
        String accountNumber,
        String role
) {
}
