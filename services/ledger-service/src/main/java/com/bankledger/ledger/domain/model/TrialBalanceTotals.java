package com.bankledger.ledger.domain.model;

import java.io.Serializable;

/**
 * Pure domain record representing atomic trial balance totals.
 *
 * @param debits  aggregate debit balances
 * @param credits aggregate credit balances
 */
public record TrialBalanceTotals(
        long debits,
        long credits
) implements Serializable {

    /**
     * Computes the difference between total debits and total credits.
     * In a balanced ledger, difference must equal 0.
     *
     * @return debits minus credits
     */
    public long difference() {
        return debits - credits;
    }
}
