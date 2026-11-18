package com.bankledger.account.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Result returned for public and secure account lookup.
 */
public record AccountLookupResult(
        UUID accountId,
        String branch,
        String accountNumber,
        String checkDigit,
        String holderName,
        String cpf,
        String bankCode,
        String bankName,
        String holderNameMasked
) {

    public static final String DEFAULT_BANK_CODE = "001";
    public static final String DEFAULT_BANK_NAME = "Nexus Bank S.A.";

    public AccountLookupResult {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(checkDigit, "checkDigit must not be null");
    }

    public AccountLookupResult(
            UUID accountId,
            String branch,
            String accountNumber,
            String checkDigit,
            String holderName,
            String cpf,
            String bankCode,
            String bankName
    ) {
        this(accountId, branch, accountNumber, checkDigit, holderName, cpf, bankCode, bankName, null);
    }

    public AccountLookupResult(
            UUID accountId,
            String branch,
            String accountNumber,
            String checkDigit,
            String holderNameMasked
    ) {
        this(accountId, branch, accountNumber, checkDigit, null, null, DEFAULT_BANK_CODE, DEFAULT_BANK_NAME, holderNameMasked);
    }

    public String agency() {
        return branch;
    }

    public String number() {
        return accountNumber;
    }

    public String digit() {
        return checkDigit;
    }
}
