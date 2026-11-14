package com.bankledger.ledger.application.service.strategy;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable pair of account UUIDs to be locked for a journal transaction.
 */
public record PostingAccountsPair(UUID accountIdA, UUID accountIdB) {

    public PostingAccountsPair {
        Objects.requireNonNull(accountIdA, "accountIdA must not be null");
        Objects.requireNonNull(accountIdB, "accountIdB must not be null");
    }
}
