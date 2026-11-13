package com.bankledger.transaction.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain representation of an HTTP idempotency record.
 */
public record IdempotencyRecord(
    UUID scope,
    UUID key,
    String endpoint,
    String requestHash,
    IdempotencyStatus status,
    UUID transactionId,
    Integer responseStatus,
    String responseBody,
    Instant createdAt,
    Instant expiresAt
) {
    public IdempotencyRecord {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static IdempotencyRecord inProgress(
        UUID scope,
        UUID key,
        String endpoint,
        String requestHash,
        Instant createdAt,
        Instant expiresAt
    ) {
        return new IdempotencyRecord(
            scope,
            key,
            endpoint,
            requestHash,
            IdempotencyStatus.IN_PROGRESS,
            null,
            null,
            null,
            createdAt,
            expiresAt
        );
    }

    public IdempotencyRecord withCompleted(
        UUID transactionId,
        int responseStatus,
        String responseBody
    ) {
        return new IdempotencyRecord(
            this.scope,
            this.key,
            this.endpoint,
            this.requestHash,
            IdempotencyStatus.COMPLETED,
            transactionId,
            responseStatus,
            responseBody,
            this.createdAt,
            this.expiresAt
        );
    }
}
