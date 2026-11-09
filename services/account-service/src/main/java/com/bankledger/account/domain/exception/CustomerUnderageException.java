package com.bankledger.account.domain.exception;

public class CustomerUnderageException extends DomainException {

    private static final long serialVersionUID = 1L;

    public CustomerUnderageException(String message) {
        super(message);
    }
}
