package com.bankledger.auth.domain.exception;

public class CredentialsAlreadyExistsException extends DomainException {
    private static final long serialVersionUID = 1L;

    public CredentialsAlreadyExistsException(String message) {
        super(message);
    }
}
