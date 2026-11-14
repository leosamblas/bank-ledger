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
        double monthlyVariationPercent,
        String currency,
        long asOfSequence,
        Instant updatedAt
) implements Serializable {

    public BalanceResponse(
            UUID accountId,
            long availableCents,
            long blockedCents,
            long dailyLimitCents,
            long usedTodayCents,
            double monthlyVariationPercent,
            long asOfSequence,
            Instant updatedAt
    ) {
        this(
                accountId,
                availableCents,
                availableCents,
                blockedCents,
                dailyLimitCents,
                usedTodayCents,
                monthlyVariationPercent,
                "BRL",
                asOfSequence,
                updatedAt
        );
    }

    public static BalanceResponse of(
            UUID accountId,
            long balance,
            String currency,
            long sequence,
            Instant updatedAt
    ) {
        return new BalanceResponse(
                accountId,
                balance,
                balance,
                0L,
                5000000L,
                0L,
                0.0,
                currency != null ? currency : "BRL",
                sequence,
                updatedAt != null ? updatedAt : Instant.now()
        );
    }

    public static BalanceResponse zero(UUID accountId) {
        return of(accountId, 0L, "BRL", 0L, Instant.now());
    }
}
