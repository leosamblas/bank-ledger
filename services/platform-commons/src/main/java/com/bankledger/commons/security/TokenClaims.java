package com.bankledger.commons.security;

import java.io.Serializable;
import java.util.UUID;

/**
 * Parsed claims from a verified JWT token.
 *
 * @param customerId    Unique identifier of the customer.
 * @param accountId     Unique identifier of the account.
 * @param cpf           Customer tax identification / CPF (or subject claim).
 * @param role          Role granted to the user (e.g. ROLE_USER, ROLE_ADMIN).
 * @param branch        Bank branch code.
 * @param accountNumber Account number.
 */
public record TokenClaims(
        UUID customerId,
        UUID accountId,
        String cpf,
        String role,
        String branch,
        String accountNumber
) implements Serializable {

    public TokenClaims(UUID customerId, UUID accountId, String branch, String accountNumber, String role) {
        this(customerId, accountId, null, role, branch, accountNumber);
    }
}
