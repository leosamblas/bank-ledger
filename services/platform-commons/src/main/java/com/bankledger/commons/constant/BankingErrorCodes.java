package com.bankledger.commons.constant;

/**
 * Centralized banking and transactional error codes across microservices.
 */
public final class BankingErrorCodes {

    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    public static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";
    public static final String LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String IDEMPOTENCY_REUSED = IDEMPOTENCY_KEY_REUSED;
    public static final String INVALID_TRANSACTION = "INVALID_TRANSACTION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String CONFLICT = "CONFLICT";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    private BankingErrorCodes() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
