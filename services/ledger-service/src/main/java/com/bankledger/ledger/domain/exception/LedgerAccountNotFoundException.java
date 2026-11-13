package com.bankledger.ledger.domain.exception;

import java.util.UUID;

/**
 * Thrown when a required ledger account is not found.
 */
public class LedgerAccountNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID accountId;

    public LedgerAccountNotFoundException(String message) {
        super(message);
        this.accountId = null;
    }

    public LedgerAccountNotFoundException(UUID accountId) {
        super("Ledger account not found with id: " + accountId);
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
