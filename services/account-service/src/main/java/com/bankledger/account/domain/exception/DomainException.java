package com.bankledger.account.domain.exception;

/**
 * Base abstract class for all domain-level business exceptions.
 * Pure Java: no framework dependencies.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
