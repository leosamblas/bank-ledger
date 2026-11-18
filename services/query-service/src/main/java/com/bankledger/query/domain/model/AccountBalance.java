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
        Instant updatedAt,
        long dailyTransferLimitCents
) implements Serializable {

    public static final String DEFAULT_CURRENCY = "BRL";
    public static final long DEFAULT_DAILY_TRANSFER_LIMIT_CENTS = 5_000_000L;

    public AccountBalance(
            UUID accountId,
            long balance,
            String currency,
            long lastSequence,
            Instant updatedAt
    ) {
        this(accountId, balance, currency, lastSequence, updatedAt, DEFAULT_DAILY_TRANSFER_LIMIT_CENTS);
    }

    public AccountBalance {
        Objects.requireNonNull(accountId, "accountId must not be null");
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (dailyTransferLimitCents <= 0) {
            dailyTransferLimitCents = DEFAULT_DAILY_TRANSFER_LIMIT_CENTS;
        }
    }

    public static AccountBalance zero(UUID accountId, Instant now) {
        return new AccountBalance(
                accountId,
                0L,
                DEFAULT_CURRENCY,
                0L,
                now != null ? now : Instant.now(),
                DEFAULT_DAILY_TRANSFER_LIMIT_CENTS
        );
    }
}
