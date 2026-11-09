package com.bankledger.account.domain.exception;

public class AccountNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String message) {
        super(message);
    }
}
