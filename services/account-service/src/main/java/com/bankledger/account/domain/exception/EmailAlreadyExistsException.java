package com.bankledger.account.domain.exception;

/**
 * Thrown when attempting to register a customer with an email that already exists.
 */
public class EmailAlreadyExistsException extends DomainException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
