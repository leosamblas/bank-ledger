package com.bankledger.account.domain.exception;

/**
 * Thrown when attempting to register a customer with a CPF that already exists.
 */
public class CpfAlreadyExistsException extends DomainException {

    private static final long serialVersionUID = 1L;

    public CpfAlreadyExistsException(String message) {
        super(message);
    }
}
