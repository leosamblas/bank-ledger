package com.bankledger.account.domain.exception;

public class InvalidCpfException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidCpfException(String message) {
        super(message);
    }
}
