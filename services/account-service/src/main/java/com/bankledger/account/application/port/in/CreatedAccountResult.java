package com.bankledger.account.application.port.in;

import com.bankledger.account.domain.model.AccountStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Result returned upon successful account creation.
 */
public record CreatedAccountResult(
        UUID accountId,
        UUID customerId,
        String branch,
        String accountNumber,
        String checkDigit,
        AccountStatus status,
        Instant createdAt
) {

    public CreatedAccountResult {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(checkDigit, "checkDigit must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public String formattedAccountNumber() {
        return branch + "/" + accountNumber + "-" + checkDigit;
    }
}
