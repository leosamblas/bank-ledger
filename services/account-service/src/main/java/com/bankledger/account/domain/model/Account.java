package com.bankledger.account.domain.model;

import com.bankledger.account.domain.exception.AccountAlreadyClosedException;
import com.bankledger.account.domain.vo.AccountNumber;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain aggregate root representing a Bank Checking Account.
 * Zero framework dependencies.
 */
public class Account implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_BRANCH = "0001";

    private final UUID id;
    private final UUID customerId;
    private final String branch;
    private final AccountNumber accountNumber;
    private AccountStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    /**
     * Full constructor for reconstitution from persistence.
     */
    public Account(
            UUID id,
            UUID customerId,
            String branch,
            AccountNumber accountNumber,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.branch = branch != null && !branch.isBlank() ? branch.trim() : DEFAULT_BRANCH;
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.closedAt = closedAt;
    }

    /**
     * Factory method to create a new active account.
     */
    public static Account create(
            UUID id,
            UUID customerId,
            String branch,
            AccountNumber accountNumber,
            Instant now
    ) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(now, "now must not be null");

        UUID accountId = id != null ? id : UUID.randomUUID();
        String accountBranch = branch != null && !branch.isBlank() ? branch.trim() : DEFAULT_BRANCH;
        if (!accountBranch.equals(accountNumber.branch())) {
            throw new IllegalArgumentException(
                    String.format("Branch '%s' does not match account number branch '%s'",
                            accountBranch, accountNumber.branch())
            );
        }

        return new Account(
                accountId,
                customerId,
                accountBranch,
                accountNumber,
                AccountStatus.ACTIVE,
                now,
                now,
                null
        );
    }

    public static Account create(UUID customerId, AccountNumber accountNumber, Instant now) {
        return create(UUID.randomUUID(), customerId, DEFAULT_BRANCH, accountNumber, now);
    }

    public static Account create(UUID customerId, AccountNumber accountNumber) {
        return create(UUID.randomUUID(), customerId, DEFAULT_BRANCH, accountNumber, Instant.now());
    }

    /**
     * Closes the account if not already closed.
     *
     * @param now timestamp of closure
     * @throws AccountAlreadyClosedException if account is already CLOSED
     */
    public void close(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == AccountStatus.CLOSED) {
            throw new AccountAlreadyClosedException(
                    String.format("Account %s is already closed (closedAt: %s)",
                            accountNumber.formatted(), closedAt)
            );
        }
        this.status = AccountStatus.CLOSED;
        this.closedAt = now;
        this.updatedAt = now;
    }

    public void close() {
        close(Instant.now());
    }

    public void suspend(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == AccountStatus.CLOSED) {
            throw new AccountAlreadyClosedException(
                    String.format("Cannot suspend an already closed account %s", accountNumber.formatted())
            );
        }
        this.status = AccountStatus.SUSPENDED;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == AccountStatus.CLOSED) {
            throw new AccountAlreadyClosedException(
                    String.format("Cannot activate an already closed account %s", accountNumber.formatted())
            );
        }
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = now;
    }

    // Standard JavaBeans getters
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getBranch() { return branch; }
    public AccountNumber getAccountNumber() { return accountNumber; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }

    // Fluent record-style accessors
    public UUID id() { return id; }
    public UUID customerId() { return customerId; }
    public String branch() { return branch; }
    public AccountNumber accountNumber() { return accountNumber; }
    public AccountStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant closedAt() { return closedAt; }
}
