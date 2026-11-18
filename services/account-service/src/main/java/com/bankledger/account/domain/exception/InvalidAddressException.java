package com.bankledger.account.domain.exception;

/**
 * Thrown when an Address Value Object fails validation invariants.
 * Zero framework dependencies.
 */
public class InvalidAddressException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidAddressException(String message) {
        super(message);
    }
}
