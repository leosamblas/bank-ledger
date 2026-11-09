package com.bankledger.account.application.port.in;

import com.bankledger.account.domain.model.AccountStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Result returned for authenticated customer account query.
 */
public record AccountMeResult(
        UUID accountId,
        UUID customerId,
        String branch,
        String accountNumber,
        String checkDigit,
        AccountStatus status,
        Instant createdAt,
        String holderName,
        String holderCpf,
        String holderEmail
) {

    public AccountMeResult {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(checkDigit, "checkDigit must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(holderName, "holderName must not be null");
        Objects.requireNonNull(holderCpf, "holderCpf must not be null");
        Objects.requireNonNull(holderEmail, "holderEmail must not be null");
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

    public Instant openedAt() {
        return createdAt;
    }
}
