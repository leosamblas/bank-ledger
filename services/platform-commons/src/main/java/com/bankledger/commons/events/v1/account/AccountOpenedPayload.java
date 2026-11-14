package com.bankledger.commons.events.v1.account;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical domain record representing the payload of an AccountOpened business event (v1).
 * Zero framework dependencies (pure Java standard library).
 */
public record AccountOpenedPayload(
        UUID accountId,
        UUID customerId,
        String customerCpf,
        String customerName,
        String branch,
        String accountNumber,
        String checkDigit,
        Instant openedAt
) implements Serializable {

    public AccountOpenedPayload {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(customerCpf, "customerCpf must not be null");
        Objects.requireNonNull(customerName, "customerName must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(checkDigit, "checkDigit must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
    }
}
