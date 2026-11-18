package com.bankledger.account.adapters.in.rest.dto;

import java.util.UUID;

/**
 * REST DTO for account lookup responses.
 * Supports canonical branch/accountNumber and agency/number aliases.
 */
public record AccountLookupResponse(
        UUID accountId,
        String branch,
        String accountNumber,
        String checkDigit,
        String holderName,
        String cpf,
        String bankCode,
        String bankName,
        String holderNameMasked,
        String agency,
        String number
) {

    public static final String DEFAULT_BANK_CODE = "001";
    public static final String DEFAULT_BANK_NAME = "Nexus Bank S.A.";

    public AccountLookupResponse(
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
        this(
                accountId,
                branch,
                accountNumber,
                checkDigit,
                holderName,
                cpf,
                bankCode,
                bankName,
                holderNameMasked,
                branch,
                accountNumber
        );
    }

    public AccountLookupResponse(
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

    public AccountLookupResponse(
            UUID accountId,
            String agency,
            String number,
            String holderNameMasked
    ) {
        this(accountId, agency, number, null, null, null, DEFAULT_BANK_CODE, DEFAULT_BANK_NAME, holderNameMasked);
    }

    public String digit() {
        return checkDigit;
    }
}
