package com.bankledger.ledger.domain.exception;

/**
 * Thrown when a journal violates the fundamental double-entry accounting equation:
 * sum(debits) must equal sum(credits) and match the total journal amount,
 * and there must be at least 2 entries.
 */
public class UnbalancedJournalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Long debitTotal;
    private final Long creditTotal;
    private final Long expectedTotal;

    public UnbalancedJournalException(String message) {
        super(message);
        this.debitTotal = null;
        this.creditTotal = null;
        this.expectedTotal = null;
    }

    public UnbalancedJournalException(long debitTotal, long creditTotal, long expectedTotal) {
        super(String.format("Unbalanced journal entries: debits=%d, credits=%d, expectedTotal=%d",
                debitTotal, creditTotal, expectedTotal));
        this.debitTotal = debitTotal;
        this.creditTotal = creditTotal;
        this.expectedTotal = expectedTotal;
    }

    public Long getDebitTotal() {
        return debitTotal;
    }

    public Long getCreditTotal() {
        return creditTotal;
    }

    public Long getExpectedTotal() {
        return expectedTotal;
    }
}
