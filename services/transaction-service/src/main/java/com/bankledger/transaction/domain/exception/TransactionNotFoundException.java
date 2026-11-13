package com.bankledger.transaction.domain.exception;

import java.util.UUID;

/**
 * Thrown when a financial transaction is not found or cannot be accessed by the requester (OWASP A01 BOLA protection).
 */
public class TransactionNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public TransactionNotFoundException(String message) {
        super(message);
    }

    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction not found: " + transactionId);
    }
}
