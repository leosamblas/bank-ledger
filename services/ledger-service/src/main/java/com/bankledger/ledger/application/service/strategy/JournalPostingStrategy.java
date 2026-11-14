package com.bankledger.ledger.application.service.strategy;

import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.model.LedgerAccount;

import java.time.Instant;
import java.util.UUID;

/**
 * Strategy interface encapsulating double-entry account resolution and entry generation
 * for specific transaction types (DEPOSIT, WITHDRAWAL, TRANSFER).
 */
public interface JournalPostingStrategy {

    /**
     * Determines whether this strategy supports the given transaction type.
     *
     * @param transactionType the transaction type name (e.g. DEPOSIT, WITHDRAWAL, TRANSFER)
     * @return true if this strategy can handle the transaction type
     */
    boolean supports(String transactionType);

    /**
     * Resolves the pair of account UUIDs that must be locked for this transaction.
     *
     * @param command the posting command
     * @return pair of account IDs to lock
     */
    PostingAccountsPair resolveAccountsToLock(PostJournalCommand command);

    /**
     * Generates the double-entry legs for this transaction, applying balance mutations to the locked accounts.
     *
     * @param command  the posting command
     * @param accountA the first locked account (corresponding to accountIdA)
     * @param accountB the second locked account (corresponding to accountIdB)
     * @param journalId the unique identifier for the journal
     * @param now       the current timestamp
     * @return posting result with generated entries or rejection information
     */
    PostingEntriesResult generateEntries(
            PostJournalCommand command,
            LedgerAccount accountA,
            LedgerAccount accountB,
            UUID journalId,
            Instant now
    );

    /**
     * Returns the human-readable description for the posted journal.
     *
     * @param command the posting command
     * @return journal description
     */
    default String getJournalDescription(PostJournalCommand command) {
        return command.type();
    }
}
