package com.bankledger.transaction.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port for HTTP request idempotency validation and locking.
 */
public interface IdempotencyValidatorUseCase {

    IdempotencyCheckResult validateOrLock(
            UUID scope,
            UUID key,
            String endpoint,
            String requestHash,
            Instant now
    );

    IdempotencyCheckResult validateOrLock(
            UUID scope,
            UUID key,
            String endpoint,
            String requestHash
    );

    void complete(
            UUID scope,
            UUID key,
            UUID transactionId,
            int responseStatus,
            String responseBody,
            Instant now
    );

    void complete(
            UUID scope,
            UUID key,
            UUID transactionId,
            int responseStatus,
            String responseBody
    );

    String computeHash(String rawBody);
}
