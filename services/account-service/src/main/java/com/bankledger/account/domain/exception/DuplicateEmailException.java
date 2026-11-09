package com.bankledger.account.domain.exception;

public class DuplicateEmailException extends DomainException {

    private static final long serialVersionUID = 1L;

    public DuplicateEmailException(String message) {
        super(message);
    }
}
