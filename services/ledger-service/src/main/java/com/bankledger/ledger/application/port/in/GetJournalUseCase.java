package com.bankledger.ledger.application.port.in;

import com.bankledger.ledger.domain.model.Journal;

import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port for querying double-entry journals by transaction ID.
 */
public interface GetJournalUseCase {

    Optional<Journal> getJournalByTransactionId(UUID transactionId);
}
