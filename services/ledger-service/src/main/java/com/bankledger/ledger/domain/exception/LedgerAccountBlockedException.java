package com.bankledger.ledger.domain.exception;

import com.bankledger.ledger.domain.model.AccountStatus;

import java.util.UUID;

/**
 * Thrown when an entry cannot be applied because the account is not in ACTIVE status (e.g. BLOCKED or CLOSED).
 */
public class LedgerAccountBlockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final AccountStatus status;

    public LedgerAccountBlockedException(String message) {
        super(message);
        this.accountId = null;
        this.status = null;
    }

    public LedgerAccountBlockedException(UUID accountId, AccountStatus status) {
        super(String.format("Ledger account %s is not active: status=%s", accountId, status));
        this.accountId = accountId;
        this.status = status;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
