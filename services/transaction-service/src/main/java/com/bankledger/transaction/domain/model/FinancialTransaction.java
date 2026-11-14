package com.bankledger.transaction.domain.model;

import com.bankledger.commons.constant.BankingConstants;

import java.io.Serializable;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain aggregate root representing a financial transaction.
 * Tracks the complete lifecycle from PENDING dispatch to COMPLETED or FAILED.
 */
public class FinancialTransaction implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_CURRENCY = BankingConstants.DEFAULT_CURRENCY;

    private static final EnumMap<TransactionStatus, EnumSet<TransactionStatus>> VALID_TRANSITIONS =
            new EnumMap<>(TransactionStatus.class);

    static {
        VALID_TRANSITIONS.put(TransactionStatus.PENDING, EnumSet.of(TransactionStatus.COMPLETED, TransactionStatus.FAILED));
        VALID_TRANSITIONS.put(TransactionStatus.COMPLETED, EnumSet.noneOf(TransactionStatus.class));
        VALID_TRANSITIONS.put(TransactionStatus.FAILED, EnumSet.noneOf(TransactionStatus.class));
    }

    private final UUID id;
    private final TransactionType type;
    private TransactionStatus status;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final long amount;
    private final String currency;
    private final String description;
    private final UUID requestedBy;
    private String failureCode;
    private String failureReason;
    private final UUID correlationId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private final long version;

    /**
     * Full constructor for reconstitution from persistence or factory initialization.
     */
    public FinancialTransaction(
            UUID id,
            TransactionType type,
            TransactionStatus status,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amount,
            String currency,
            String description,
            UUID requestedBy,
            String failureCode,
            String failureReason,
            UUID correlationId,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero, was: " + amount);
        }
        this.amount = amount;
        this.currency = (currency != null && !currency.isBlank()) ? currency : DEFAULT_CURRENCY;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.description = description;
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.completedAt = completedAt;
        this.version = version;
    }

    /**
     * Factory to instantiate a new DEPOSIT transaction in PENDING status.
     */
    public static FinancialTransaction createDeposit(
            UUID id,
            UUID destinationAccountId,
            long amount,
            String description,
            UUID requestedBy,
            UUID correlationId,
            Instant now
    ) {
        UUID txId = id != null ? id : UUID.randomUUID();
        Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero, was: " + amount);
        }
        UUID corrId = correlationId != null ? correlationId : UUID.randomUUID();
        Instant timestamp = now != null ? now : Instant.now();

        return new FinancialTransaction(
                txId,
                TransactionType.DEPOSIT,
                TransactionStatus.PENDING,
                null,
                destinationAccountId,
                amount,
                DEFAULT_CURRENCY,
                description,
                requestedBy,
                null,
                null,
                corrId,
                timestamp,
                timestamp,
                null,
                0L
        );
    }

    /**
     * Factory to instantiate a new WITHDRAWAL transaction in PENDING status.
     */
    public static FinancialTransaction createWithdrawal(
            UUID id,
            UUID sourceAccountId,
            long amount,
            String description,
            UUID requestedBy,
            UUID correlationId,
            Instant now
    ) {
        UUID txId = id != null ? id : UUID.randomUUID();
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero, was: " + amount);
        }
        UUID corrId = correlationId != null ? correlationId : UUID.randomUUID();
        Instant timestamp = now != null ? now : Instant.now();

        return new FinancialTransaction(
                txId,
                TransactionType.WITHDRAWAL,
                TransactionStatus.PENDING,
                sourceAccountId,
                null,
                amount,
                DEFAULT_CURRENCY,
                description,
                requestedBy,
                null,
                null,
                corrId,
                timestamp,
                timestamp,
                null,
                0L
        );
    }

    /**
     * Transition transaction to COMPLETED.
     */
    public void complete(Instant now) {
        transitionTo(TransactionStatus.COMPLETED);
        Instant timestamp = now != null ? now : Instant.now();
        this.completedAt = timestamp;
        this.updatedAt = timestamp;
    }

    /**
     * Transition transaction to FAILED with failure reason.
     */
    public void fail(String failureCode, String failureReason, Instant now) {
        transitionTo(TransactionStatus.FAILED);
        Instant timestamp = now != null ? now : Instant.now();
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.completedAt = timestamp;
        this.updatedAt = timestamp;
    }

    private void transitionTo(TransactionStatus target) {
        EnumSet<TransactionStatus> allowed = VALID_TRANSITIONS.getOrDefault(this.status, EnumSet.noneOf(TransactionStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Illegal transaction transition from " + this.status + " to " + target);
        }
        this.status = target;
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinancialTransaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FinancialTransaction{" +
                "id=" + id +
                ", type=" + type +
                ", status=" + status +
                ", sourceAccountId=" + sourceAccountId +
                ", destinationAccountId=" + destinationAccountId +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", description='" + description + '\'' +
                ", requestedBy=" + requestedBy +
                ", failureCode='" + failureCode + '\'' +
                ", failureReason='" + failureReason + '\'' +
                ", correlationId=" + correlationId +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", completedAt=" + completedAt +
                ", version=" + version +
                '}';
    }
}
