package com.bankledger.ledger.domain.model;

import com.bankledger.ledger.domain.exception.InsufficientFundsException;
import com.bankledger.ledger.domain.exception.LedgerAccountBlockedException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain aggregate representing a ledger book-of-accounts account.
 * Enforces accounting normal balance rules, sequential entry numbering,
 * customer non-negative balance invariant, and operational status checks.
 * Zero framework dependencies.
 */
public class LedgerAccount {

    public static final String DEFAULT_CURRENCY = "BRL";

    private final UUID id;
    private final String code;
    private final AccountKind kind;
    private final NormalBalance normalBalance;
    private final String currency;
    private long balance;
    private long lastSequence;
    private AccountStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public LedgerAccount(
            UUID id,
            String code,
            AccountKind kind,
            NormalBalance normalBalance,
            String currency,
            long balance,
            long lastSequence,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.normalBalance = Objects.requireNonNull(normalBalance, "normalBalance must not be null");
        this.currency = (currency != null && !currency.isBlank()) ? currency : DEFAULT_CURRENCY;
        this.balance = balance;
        this.lastSequence = lastSequence;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now(Clock.systemUTC());
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.version = version;
    }

    /**
     * Factory to instantiate an initial active customer account with balance 0 and sequence 0.
     */
    public static LedgerAccount createCustomerInitial(UUID id, String code) {
        Instant now = Instant.now(Clock.systemUTC());
        return new LedgerAccount(
                id,
                code,
                AccountKind.CUSTOMER,
                NormalBalance.CREDIT,
                DEFAULT_CURRENCY,
                0L,
                0L,
                AccountStatus.ACTIVE,
                now,
                now,
                0L
        );
    }

    /**
     * Applies a double-entry leg to this account.
     * Validates amount, checks account status, adjusts balance based on normal balance rules,
     * validates customer non-negative balance invariant, and increments entry sequence.
     *
     * @param direction DEBIT or CREDIT
     * @param amount    positive amount in cents
     * @return EntryApplicationResult containing new balanceAfter and sequence
     */
    public synchronized EntryApplicationResult applyEntry(EntryDirection direction, long amount) {
        Objects.requireNonNull(direction, "direction must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero, was: " + amount);
        }

        if (this.status != AccountStatus.ACTIVE) {
            throw new LedgerAccountBlockedException(this.id, this.status);
        }

        long newBalance;
        if (this.normalBalance == NormalBalance.CREDIT) {
            if (direction == EntryDirection.CREDIT) {
                newBalance = this.balance + amount;
            } else {
                newBalance = this.balance - amount;
                if (this.kind == AccountKind.CUSTOMER && newBalance < 0) {
                    throw new InsufficientFundsException(this.id, this.balance, amount);
                }
            }
        } else {
            // NormalBalance.DEBIT (e.g. System cash account)
            if (direction == EntryDirection.DEBIT) {
                newBalance = this.balance + amount;
            } else {
                newBalance = this.balance - amount;
                if (this.kind == AccountKind.CUSTOMER && newBalance < 0) {
                    throw new InsufficientFundsException(this.id, this.balance, amount);
                }
            }
        }

        this.balance = newBalance;
        this.lastSequence = this.lastSequence + 1;
        this.updatedAt = Instant.now(Clock.systemUTC());

        return new EntryApplicationResult(this.balance, this.lastSequence);
    }

    public void updateStatus(AccountStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "status must not be null");
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public AccountKind getKind() {
        return kind;
    }

    public NormalBalance getNormalBalance() {
        return normalBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public synchronized long getBalance() {
        return balance;
    }

    public synchronized long getLastSequence() {
        return lastSequence;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String code;
        private AccountKind kind;
        private NormalBalance normalBalance;
        private String currency = DEFAULT_CURRENCY;
        private long balance = 0L;
        private long lastSequence = 0L;
        private AccountStatus status = AccountStatus.ACTIVE;
        private Instant createdAt;
        private Instant updatedAt;
        private long version = 0L;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder kind(AccountKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder normalBalance(NormalBalance normalBalance) {
            this.normalBalance = normalBalance;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder balance(long balance) {
            this.balance = balance;
            return this;
        }

        public Builder lastSequence(long lastSequence) {
            this.lastSequence = lastSequence;
            return this;
        }

        public Builder status(AccountStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public LedgerAccount build() {
            return new LedgerAccount(
                    id, code, kind, normalBalance, currency,
                    balance, lastSequence, status, createdAt, updatedAt, version
            );
        }
    }
}
