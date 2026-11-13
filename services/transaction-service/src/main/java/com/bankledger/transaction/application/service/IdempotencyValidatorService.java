package com.bankledger.transaction.application.service;

import com.bankledger.transaction.application.port.in.IdempotencyCheckResult;
import com.bankledger.transaction.application.port.in.IdempotencyValidatorUseCase;
import com.bankledger.transaction.application.port.out.IdempotencyRepositoryPort;
import com.bankledger.transaction.domain.exception.IdempotencyConflictException;
import com.bankledger.transaction.domain.exception.IdempotencyKeyReusedException;
import com.bankledger.transaction.domain.model.IdempotencyRecord;
import com.bankledger.transaction.domain.model.IdempotencyStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyValidatorService implements IdempotencyValidatorUseCase {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final Clock clock;

    public IdempotencyValidatorService(IdempotencyRepositoryPort idempotencyRepositoryPort, Clock clock) {
        this.idempotencyRepositoryPort = Objects.requireNonNull(idempotencyRepositoryPort, "idempotencyRepositoryPort must not be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyCheckResult validateOrLock(
            UUID scope,
            UUID key,
            String endpoint,
            String requestHash,
            Instant now
    ) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(now, "now must not be null");

        Optional<IdempotencyRecord> maybeRecord = idempotencyRepositoryPort.findByScopeAndKey(scope, key);

        if (maybeRecord.isEmpty()) {
            Instant expiresAt = now.plus(DEFAULT_TTL);
            IdempotencyRecord newRecord = IdempotencyRecord.inProgress(
                    scope,
                    key,
                    endpoint,
                    requestHash,
                    now,
                    expiresAt
            );
            idempotencyRepositoryPort.save(newRecord);
            return IdempotencyCheckResult.proceed();
        }

        IdempotencyRecord existing = maybeRecord.get();

        if (existing.status() == IdempotencyStatus.IN_PROGRESS) {
            throw new IdempotencyConflictException();
        }

        if (existing.status() == IdempotencyStatus.COMPLETED) {
            if (existing.requestHash().equals(requestHash)) {
                return IdempotencyCheckResult.cached(
                        existing.responseStatus(),
                        existing.responseBody(),
                        existing.transactionId()
                );
            }
            throw new IdempotencyKeyReusedException();
        }

        return IdempotencyCheckResult.proceed();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyCheckResult validateOrLock(UUID scope, UUID key, String endpoint, String requestHash) {
        return validateOrLock(scope, key, endpoint, requestHash, clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            UUID scope,
            UUID key,
            UUID transactionId,
            int responseStatus,
            String responseBody,
            Instant now
    ) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");

        IdempotencyRecord existing = idempotencyRepositoryPort.findByScopeAndKey(scope, key)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record not found for scope " + scope + " and key " + key
                ));

        IdempotencyRecord completed = existing.withCompleted(transactionId, responseStatus, responseBody);
        idempotencyRepositoryPort.save(completed);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID scope, UUID key, UUID transactionId, int responseStatus, String responseBody) {
        complete(scope, key, transactionId, responseStatus, responseBody, clock.instant());
    }

    @Override
    public String computeHash(String rawBody) {
        String content = rawBody != null ? rawBody : "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
