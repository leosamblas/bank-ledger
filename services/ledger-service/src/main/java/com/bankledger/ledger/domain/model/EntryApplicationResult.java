package com.bankledger.ledger.domain.model;

/**
 * Result of applying an entry to a ledger account.
 *
 * @param balanceAfter the resulting account balance in cents
 * @param sequence     the sequential entry counter assigned to this entry
 */
public record EntryApplicationResult(
        long balanceAfter,
        long sequence
) {}
