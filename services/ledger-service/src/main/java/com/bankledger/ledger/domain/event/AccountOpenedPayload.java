package com.bankledger.ledger.domain.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain record representing the payload of an AccountOpened event consumed from account-service.
 * Zero framework dependencies.
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
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(checkDigit, "checkDigit must not be null");
    }
}
