package com.bankledger.ledger.domain.model;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.ledger.domain.exception.UnbalancedJournalException;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain aggregate representing a double-entry accounting journal.
 * Holds the transaction summary, status, and immutable journal entries (legs).
 * Zero framework dependencies.
 */
public class Journal {

    public static final String DEFAULT_CURRENCY = BankingConstants.DEFAULT_CURRENCY;

    private final UUID id;
    private final UUID transactionId;
    private final String type;
    private final JournalStatus status;
    private final String rejectCode;
    private final String rejectReason;
    private final String description;
    private final long totalAmount;
    private final String currency;
    private final UUID correlationId;
    private final Instant postedAt;
    private final List<Entry> entries;

    public Journal(
            UUID id,
            UUID transactionId,
            String type,
            JournalStatus status,
            String rejectCode,
            String rejectReason,
            String description,
            long totalAmount,
            String currency,
            UUID correlationId,
            Instant postedAt,
            List<Entry> entries
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.rejectCode = rejectCode;
        this.rejectReason = rejectReason;
        this.description = description;
        this.totalAmount = totalAmount;
        this.currency = (currency != null && !currency.isBlank()) ? currency : DEFAULT_CURRENCY;
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.postedAt = postedAt != null ? postedAt : Instant.now(Clock.systemUTC());
        this.entries = (entries != null) ? List.copyOf(entries) : Collections.emptyList();
    }

    /**
     * Factory to create a balanced, POSTED double-entry journal.
     * Enforces that there are at least 2 entries, and sum(debits) == sum(credits) == totalAmount.
     */
    public static Journal createPosted(
            UUID id,
            UUID transactionId,
            String type,
            String description,
            long totalAmount,
            String currency,
            UUID correlationId,
            Instant postedAt,
            List<Entry> entries
    ) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Entries must not be null or empty");
        }
        if (entries.size() < 2) {
            throw new UnbalancedJournalException("Journal must have at least 2 entries, got: " + entries.size());
        }
        if (totalAmount <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than zero, was: " + totalAmount);
        }

        long debitSum = entries.stream()
                .filter(e -> e.direction() == EntryDirection.DEBIT)
                .mapToLong(Entry::amount)
                .sum();

        long creditSum = entries.stream()
                .filter(e -> e.direction() == EntryDirection.CREDIT)
                .mapToLong(Entry::amount)
                .sum();

        if (debitSum != creditSum || debitSum != totalAmount) {
            throw new UnbalancedJournalException(debitSum, creditSum, totalAmount);
        }

        return new Journal(
                id,
                transactionId,
                type,
                JournalStatus.POSTED,
                null,
                null,
                description,
                totalAmount,
                currency,
                correlationId,
                postedAt,
                entries
        );
    }

    /**
     * Factory to create a REJECTED journal without entry legs.
     */
    public static Journal createRejected(
            UUID id,
            UUID transactionId,
            String type,
            String description,
            long totalAmount,
            String currency,
            UUID correlationId,
            Instant postedAt,
            String rejectCode,
            String rejectReason
    ) {
        return new Journal(
                id,
                transactionId,
                type,
                JournalStatus.REJECTED,
                rejectCode,
                rejectReason,
                description,
                totalAmount,
                currency,
                correlationId,
                postedAt,
                Collections.emptyList()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getType() {
        return type;
    }

    public JournalStatus getStatus() {
        return status;
    }

    public String getRejectCode() {
        return rejectCode;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getDescription() {
        return description;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public List<Entry> getEntries() {
        return entries;
    }
}
