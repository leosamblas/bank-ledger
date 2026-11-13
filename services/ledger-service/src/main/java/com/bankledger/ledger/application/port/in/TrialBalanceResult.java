package com.bankledger.ledger.application.port.in;

import java.io.Serializable;

/**
 * Result record representing the double-entry trial balance.
 *
 * @param debits     sum of all balances with normal balance DEBIT
 * @param credits    sum of all balances with normal balance CREDIT
 * @param difference debits minus credits (must equal 0 in a balanced ledger)
 */
public record TrialBalanceResult(
        long debits,
        long credits,
        long difference
) implements Serializable {
}
