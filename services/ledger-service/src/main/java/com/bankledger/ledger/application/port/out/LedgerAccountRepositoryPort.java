package com.bankledger.ledger.application.port.out;

import com.bankledger.ledger.domain.model.LedgerAccount;

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
     * Persists or updates a ledger account.
     */
    LedgerAccount save(LedgerAccount account);
}
