package com.bankledger.transaction.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain record representing an account's approved daily transfer limit.
 */
public record AccountLimit(
        UUID accountId,
        long dailyTransferLimitCents,
        String approvedBy,
        Instant updatedAt
) {
    public static final long DEFAULT_DAILY_TRANSFER_LIMIT_CENTS = 5_000_000L;
    public static final String DEFAULT_APPROVED_BY = "SYSTEM_DEFAULT";

    public AccountLimit {
        Objects.requireNonNull(accountId, "accountId must not be null");
        if (dailyTransferLimitCents <= 0) {
            throw new IllegalArgumentException("dailyTransferLimitCents must be positive");
        }
        if (approvedBy == null || approvedBy.isBlank()) {
            approvedBy = DEFAULT_APPROVED_BY;
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    public static AccountLimit defaultFor(UUID accountId) {
        return new AccountLimit(accountId, DEFAULT_DAILY_TRANSFER_LIMIT_CENTS, DEFAULT_APPROVED_BY, Instant.now());
    }
}
