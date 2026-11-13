package com.bankledger.ledger.application.port.out;

import com.bankledger.ledger.domain.model.Journal;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound repository port for persisting and querying journals and their entry legs.
 */
public interface JournalRepositoryPort {

    /**
     * Persists a journal aggregate and its immutable entries.
     */
    Journal save(Journal journal);

    /**
     * Finds a journal by the business transaction id to support idempotency checks.
     */
    Optional<Journal> findByTransactionId(UUID transactionId);
}
