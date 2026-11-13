package com.bankledger.transaction.application.port.in;

import java.util.UUID;

public record IdempotencyCheckResult(
    boolean shouldProceed,
    Integer responseStatus,
    String responseBody,
    UUID transactionId
) {
    public static IdempotencyCheckResult proceed() {
        return new IdempotencyCheckResult(true, null, null, null);
    }

    public static IdempotencyCheckResult cached(Integer responseStatus, String responseBody, UUID transactionId) {
        return new IdempotencyCheckResult(false, responseStatus, responseBody, transactionId);
    }

    public boolean isProceed() {
        return shouldProceed;
    }

    public boolean isCached() {
        return !shouldProceed;
    }

    public Integer cachedStatus() {
        return responseStatus;
    }

    public String cachedBody() {
        return responseBody;
    }

    public UUID cachedTransactionId() {
        return transactionId;
    }
}
