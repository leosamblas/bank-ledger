package com.bankledger.account.domain.exception;

/**
 * Thrown when authentication fails due to invalid CPF or password.
 */
public class InvalidCredentialsException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
