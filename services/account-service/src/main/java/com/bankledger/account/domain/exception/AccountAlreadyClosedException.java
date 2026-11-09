package com.bankledger.account.domain.exception;

public class AccountAlreadyClosedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AccountAlreadyClosedException(String message) {
        super(message);
    }
}
