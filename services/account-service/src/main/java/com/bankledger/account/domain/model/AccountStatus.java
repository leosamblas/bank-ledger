package com.bankledger.account.domain.model;

/**
 * Status of a bank account in its lifecycle.
 * Pure domain enum: zero framework dependencies.
 */
public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    SUSPENDED,
    BLOCKED,
    CLOSED
}
