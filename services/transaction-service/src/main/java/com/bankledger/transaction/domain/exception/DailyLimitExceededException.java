package com.bankledger.transaction.domain.exception;

import java.util.UUID;

/**
 * Domain exception thrown when a transfer exceeds the account's approved daily limit.
 */
public class DailyLimitExceededException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final long approvedLimitCents;
    private final long usedTodayCents;
    private final long requestedAmountCents;

    public DailyLimitExceededException(UUID accountId, long approvedLimitCents, long usedTodayCents, long requestedAmountCents) {
        super(String.format(
                "Daily transfer limit exceeded for account %s: requested amount %d cents, already used today %d cents, approved daily limit is %d cents",
                accountId, requestedAmountCents, usedTodayCents, approvedLimitCents
        ));
        this.accountId = accountId;
        this.approvedLimitCents = approvedLimitCents;
        this.usedTodayCents = usedTodayCents;
        this.requestedAmountCents = requestedAmountCents;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getApprovedLimitCents() {
        return approvedLimitCents;
    }

    public long getUsedTodayCents() {
        return usedTodayCents;
    }

    public long getRequestedAmountCents() {
        return requestedAmountCents;
    }
}
