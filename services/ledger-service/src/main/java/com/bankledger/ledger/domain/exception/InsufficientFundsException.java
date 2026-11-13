package com.bankledger.ledger.domain.exception;

import java.util.UUID;

/**
 * Thrown when an account has insufficient funds to fulfill a debit operation.
 */
public class InsufficientFundsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final Long currentBalance;
    private final Long requestedDebit;

    public InsufficientFundsException(String message) {
        super(message);
        this.accountId = null;
        this.currentBalance = null;
        this.requestedDebit = null;
    }

    public InsufficientFundsException(UUID accountId, long currentBalance, long requestedDebit) {
        super(String.format("Account %s has insufficient funds: balance=%d, requested=%d",
                accountId, currentBalance, requestedDebit));
        this.accountId = accountId;
        this.currentBalance = currentBalance;
        this.requestedDebit = requestedDebit;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Long getCurrentBalance() {
        return currentBalance;
    }

    public Long getRequestedDebit() {
        return requestedDebit;
    }
}
