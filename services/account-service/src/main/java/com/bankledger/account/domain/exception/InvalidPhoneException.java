package com.bankledger.account.domain.exception;

public class InvalidPhoneException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidPhoneException(String message) {
        super(message);
    }
}
