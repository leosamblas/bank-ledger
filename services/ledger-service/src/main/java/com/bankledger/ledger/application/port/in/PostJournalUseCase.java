package com.bankledger.ledger.application.port.in;

import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.model.Journal;

/**
 * Inbound port to post double-entry accounting transactions.
 */
public interface PostJournalUseCase {

    /**
     * Posts a double-entry accounting journal atomically.
     *
     * @param command request parameters for posting
     * @return posted or rejected Journal aggregate
     */
    Journal postJournal(PostJournalCommand command);
}
