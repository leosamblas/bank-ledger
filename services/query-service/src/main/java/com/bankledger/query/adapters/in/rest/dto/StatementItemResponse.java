package com.bankledger.query.adapters.in.rest.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record StatementItemResponse(
        UUID id,
        long sequence,
        UUID transactionId,
        UUID journalId,
        Instant timestamp,
        Instant postedAt,
        String category,
        String type,
        String description,
        String counterpartyName,
        String counterpartyDocumentMasked,
        String counterpartyAccount,
        String nature,
        String direction,
        long amountCents,
        long amount,
        long balanceAfterCents,
        long balanceAfter,
        String idempotencyKey,
        String failureReason
) implements Serializable {

    public StatementItemResponse(
            UUID id,
            long sequence,
            UUID transactionId,
            UUID journalId,
            Instant timestamp,
            String category,
            String type,
            String description,
            String counterpartyName,
            String counterpartyDocumentMasked,
            String counterpartyAccount,
            String nature,
            long amountCents,
            long balanceAfterCents,
            String idempotencyKey
    ) {
        this(
                id,
                sequence,
                transactionId,
                journalId,
                timestamp,
                timestamp,
                category,
                type,
                description,
                counterpartyName,
                counterpartyDocumentMasked,
                counterpartyAccount,
                nature,
                nature,
                amountCents,
                amountCents,
                balanceAfterCents,
                balanceAfterCents,
                idempotencyKey,
                null
        );
    }
}
