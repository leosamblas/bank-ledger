package com.bankledger.commons.events.v1.account;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical domain record representing the payload of an AccountReady business event (v1).
 * Emitted when credentials and account provisioning are completed and the account is ready to be used.
 * Zero framework dependencies (pure Java standard library).
 */
public record AccountReadyPayload(
        UUID customerId,
        UUID accountId,
        String email,
        String fullName,
        String branch,
        String accountNumber,
        Instant readyAt
) implements Serializable {

    public AccountReadyPayload {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        if (readyAt == null) {
            readyAt = Instant.now();
        }
    }
}
