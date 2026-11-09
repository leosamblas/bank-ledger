package com.bankledger.account.application.port.in;

import java.util.Objects;

/**
 * Inbound query for looking up an account by branch and number.
 */
public record LookupAccountQuery(
        String branch,
        String accountNumber
) {

    public LookupAccountQuery {
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
    }
}
