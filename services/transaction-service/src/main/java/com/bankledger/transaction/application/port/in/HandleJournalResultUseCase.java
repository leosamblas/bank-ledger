package com.bankledger.transaction.application.port.in;

import com.bankledger.transaction.domain.event.JournalResultPayload;

/**
 * Inbound port for handling journal execution results from the ledger engine.
 */
public interface HandleJournalResultUseCase {

    /**
     * Resolves the financial transaction corresponding to the journal result payload.
     * Transitions transaction status to COMPLETED (if POSTED) or FAILED (if REJECTED).
     *
     * @param payload the journal result payload
     */
    void handleResult(JournalResultPayload payload);
}
