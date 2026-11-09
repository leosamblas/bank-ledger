package com.bankledger.account.domain.exception;

public class InvalidAccountNumberException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidAccountNumberException(String message) {
        super(message);
    }
}
