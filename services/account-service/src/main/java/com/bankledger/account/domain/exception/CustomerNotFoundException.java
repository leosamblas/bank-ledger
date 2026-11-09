package com.bankledger.account.domain.exception;

public class CustomerNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public CustomerNotFoundException(String message) {
        super(message);
    }
}
