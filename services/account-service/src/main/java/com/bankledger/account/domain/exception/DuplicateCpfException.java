package com.bankledger.account.domain.exception;

public class DuplicateCpfException extends CpfAlreadyExistsException {

    private static final long serialVersionUID = 1L;

    public DuplicateCpfException(String message) {
        super(message);
    }
}
