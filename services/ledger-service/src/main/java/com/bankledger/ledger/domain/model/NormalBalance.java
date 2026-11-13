package com.bankledger.ledger.domain.model;

/**
 * Normal balance expectation according to standard double-entry accounting rules:
 * - Assets/Expenses have normal balance DEBIT.
 * - Liabilities/Equity/Revenue have normal balance CREDIT.
 */
public enum NormalBalance {
    DEBIT,
    CREDIT
}
