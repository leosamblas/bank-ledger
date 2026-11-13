package com.bankledger.transaction.domain.exception;

public class IdempotencyConflictException extends DomainException {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_MESSAGE = "Operação em processamento com a chave de idempotência informada.";

    public IdempotencyConflictException() {
        super(DEFAULT_MESSAGE);
    }

    public IdempotencyConflictException(String message) {
        super(message);
    }

    public IdempotencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
