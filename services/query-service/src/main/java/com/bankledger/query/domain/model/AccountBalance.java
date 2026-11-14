package com.bankledger.query.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain record representing the CQRS read-model of an account balance.
 */
public record AccountBalance(
        UUID accountId,
        long balance,
        String currency,
        long lastSequence,
        Instant updatedAt
) implements Serializable {

    public static final String DEFAULT_CURRENCY = "BRL";

    public AccountBalance {
        Objects.requireNonNull(accountId, "accountId must not be null");
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    public static AccountBalance zero(UUID accountId, Instant now) {
        return new AccountBalance(
                accountId,
                0L,
                DEFAULT_CURRENCY,
                0L,
                now != null ? now : Instant.now()
        );
    }
}
