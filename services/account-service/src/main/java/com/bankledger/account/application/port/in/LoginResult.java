package com.bankledger.account.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Result returned upon successful customer authentication.
 */
public record LoginResult(
        String token,
        Long expiresIn,
        UUID customerId,
        UUID accountId,
        String branch,
        String accountNumber,
        String customerName
) {

    public LoginResult {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(expiresIn, "expiresIn must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(customerName, "customerName must not be null");
    }

    public String fullName() {
        return customerName;
    }
}
