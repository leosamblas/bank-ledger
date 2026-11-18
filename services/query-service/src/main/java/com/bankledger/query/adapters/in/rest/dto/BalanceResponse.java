package com.bankledger.query.adapters.in.rest.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        long balance,
        long availableCents,
        long blockedCents,
        long dailyLimitCents,
        long usedTodayCents,
        long availableLimitCents,
        double monthlyVariationPercent,
        String currency,
        long asOfSequence,
        Instant updatedAt
) implements Serializable {

    public static final long DEFAULT_DAILY_LIMIT_CENTS = 5_000_000L;

    public static BalanceResponse of(
            UUID accountId,
            long balance,
            String currency,
            long sequence,
            Instant updatedAt,
            long dailyLimitCents,
            long usedTodayCents
    ) {
        long effectiveLimit = dailyLimitCents > 0 ? dailyLimitCents : DEFAULT_DAILY_LIMIT_CENTS;
        long availableLimit = Math.max(0L, effectiveLimit - usedTodayCents);
        return new BalanceResponse(
                accountId,
                balance,
                balance,
                0L,
                effectiveLimit,
                usedTodayCents,
                availableLimit,
                0.0,
                currency != null ? currency : "BRL",
                sequence,
                updatedAt != null ? updatedAt : Instant.now()
        );
    }

    public static BalanceResponse of(
            UUID accountId,
            long balance,
            String currency,
            long sequence,
            Instant updatedAt
    ) {
        return of(accountId, balance, currency, sequence, updatedAt, DEFAULT_DAILY_LIMIT_CENTS, 0L);
    }

    public static BalanceResponse zero(UUID accountId) {
        return of(accountId, 0L, "BRL", 0L, Instant.now(), DEFAULT_DAILY_LIMIT_CENTS, 0L);
    }

    public static BalanceResponse zero(UUID accountId, long dailyLimitCents) {
        return of(accountId, 0L, "BRL", 0L, Instant.now(), dailyLimitCents, 0L);
    }
}
