package com.bankledger.account.domain.exception;

public class InvalidEmailException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidEmailException(String message) {
        super(message);
    }
}
