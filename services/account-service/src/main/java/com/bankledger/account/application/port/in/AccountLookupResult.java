package com.bankledger.account.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Result returned for public account lookup with masked holder name.
 */
public record AccountLookupResult(
        UUID accountId,
        String branch,
        String accountNumber,
        String checkDigit,
        String holderNameMasked
) {

    public AccountLookupResult {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(checkDigit, "checkDigit must not be null");
        Objects.requireNonNull(holderNameMasked, "holderNameMasked must not be null");
    }

    public String agency() {
        return branch;
    }

    public String number() {
        return accountNumber;
    }
}
