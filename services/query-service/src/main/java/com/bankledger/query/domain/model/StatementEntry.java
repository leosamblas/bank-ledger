package com.bankledger.query.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain record representing a single historical statement line in the CQRS projection.
 */
public record StatementEntry(
        UUID id,
        UUID accountId,
        long sequence,
        UUID journalId,
        UUID transactionId,
        String category,
        String type,
        String direction,
        long amount,
        long balanceAfter,
        String counterpartyName,
        String counterpartyDocumentMasked,
        String counterpartyAccount,
        String description,
        String idempotencyKey,
        String failureReason,
        Instant postedAt,
        String protocolNumber,
        String counterpartyDocument
) implements Serializable {

    public StatementEntry(
            UUID id,
            UUID accountId,
            long sequence,
            UUID journalId,
            UUID transactionId,
            String category,
            String type,
            String direction,
            long amount,
            long balanceAfter,
            String counterpartyName,
            String counterpartyDocumentMasked,
            String counterpartyAccount,
            String description,
            String idempotencyKey,
            String failureReason,
            Instant postedAt
    ) {
        this(
                id,
                accountId,
                sequence,
                journalId,
                transactionId,
                category,
                type,
                direction,
                amount,
                balanceAfter,
                counterpartyName,
                counterpartyDocumentMasked,
                counterpartyAccount,
                description,
                idempotencyKey,
                failureReason,
                postedAt,
                null,
                null
        );
    }

    public StatementEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(journalId, "journalId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(postedAt, "postedAt must not be null");
    }
}
