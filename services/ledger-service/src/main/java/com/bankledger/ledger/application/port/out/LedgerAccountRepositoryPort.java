package com.bankledger.ledger.application.port.out;

import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.TrialBalanceTotals;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound repository port for accessing and persisting ledger accounts.
 */
public interface LedgerAccountRepositoryPort {

    /**
     * Finds a ledger account by id without row lock.
     */
    Optional<LedgerAccount> findById(UUID id);

    /**
     * Finds a ledger account by id acquiring a pessimistic write lock (SELECT ... FOR UPDATE).
     */
    Optional<LedgerAccount> findByIdForUpdate(UUID id);

    /**
     * Finds a ledger account by its unique code.
     */
    Optional<LedgerAccount> findByCode(String code);

    /**
     * Persists or updates a ledger account.
     */
    LedgerAccount save(LedgerAccount account);

    /**
     * Calculates atomic trial balance totals (aggregate debits and credits in a single query).
     */
    TrialBalanceTotals getTrialBalanceTotals();

    /**
     * Returns the sum of balances for all accounts with DEBIT normal balance.
     */
    default long sumDebits() {
        return getTrialBalanceTotals().debits();
    }

    /**
     * Returns the sum of balances for all accounts with CREDIT normal balance.
     */
    default long sumCredits() {
        return getTrialBalanceTotals().credits();
    }
}
