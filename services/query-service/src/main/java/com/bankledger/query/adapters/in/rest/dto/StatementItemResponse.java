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
}
