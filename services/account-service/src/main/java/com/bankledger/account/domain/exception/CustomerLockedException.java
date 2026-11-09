package com.bankledger.account.domain.exception;

public class CustomerLockedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public CustomerLockedException(String message) {
        super(message);
    }
}
