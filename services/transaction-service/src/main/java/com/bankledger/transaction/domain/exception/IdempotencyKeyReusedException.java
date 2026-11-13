package com.bankledger.transaction.domain.exception;

public class IdempotencyKeyReusedException extends DomainException {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_MESSAGE = "Chave de idempotência já utilizada com um payload diferente.";

    public IdempotencyKeyReusedException() {
        super(DEFAULT_MESSAGE);
    }

    public IdempotencyKeyReusedException(String message) {
        super(message);
    }

    public IdempotencyKeyReusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
