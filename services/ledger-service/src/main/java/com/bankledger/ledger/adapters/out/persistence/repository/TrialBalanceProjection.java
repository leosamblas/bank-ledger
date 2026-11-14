package com.bankledger.ledger.adapters.out.persistence.repository;

/**
 * Spring Data JPA projection for aggregate trial balance query.
 */
public interface TrialBalanceProjection {

    /**
     * @return sum of balances for all accounts with DEBIT normal balance
     */
    long getDebits();

    /**
     * @return sum of balances for all accounts with CREDIT normal balance
     */
    long getCredits();
}
