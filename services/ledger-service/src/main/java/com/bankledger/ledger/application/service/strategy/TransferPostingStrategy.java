package com.bankledger.ledger.application.service.strategy;

import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.exception.InsufficientFundsException;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryApplicationResult;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.LedgerAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Strategy handling TRANSFER transactions between two customer accounts.
 * Debits the source account and credits the destination account.
 * Catches insufficient funds on source account and yields rejection result.
 */
@Slf4j
@Component
public class TransferPostingStrategy implements JournalPostingStrategy {

    public static final String TRANSACTION_TYPE = "TRANSFER";
    public static final String DESCRIPTION = "Transferência contábil";
    public static final String REJECTION_CODE = "INSUFFICIENT_FUNDS";
    public static final String REJECTION_REASON = "Saldo insuficiente para efetuar a transferência.";

    @Override
    public boolean supports(String transactionType) {
        return TRANSACTION_TYPE.equalsIgnoreCase(transactionType);
    }

    @Override
    public PostingAccountsPair resolveAccountsToLock(PostJournalCommand command) {
        if (command.sourceAccountId() == null) {
            throw new IllegalArgumentException("Source account ID is required for transfer");
        }
        if (command.destinationAccountId() == null) {
            throw new IllegalArgumentException("Destination account ID is required for transfer");
        }
        return new PostingAccountsPair(command.sourceAccountId(), command.destinationAccountId());
    }

    @Override
    public PostingEntriesResult generateEntries(
            PostJournalCommand command,
            LedgerAccount sourceAccount,
            LedgerAccount destAccount,
            UUID journalId,
            Instant now
    ) {
        EntryApplicationResult sourceResult;
        try {
            sourceResult = sourceAccount.applyEntry(EntryDirection.DEBIT, command.amount());
        } catch (InsufficientFundsException ex) {
            log.warn("Transfer rejected due to insufficient funds: transactionId={}, accountId={}",
                    command.transactionId(), sourceAccount.getId());
            return PostingEntriesResult.rejected(REJECTION_CODE, REJECTION_REASON);
        }

        EntryApplicationResult destResult = destAccount.applyEntry(EntryDirection.CREDIT, command.amount());

        Entry sourceEntry = new Entry(
                UUID.randomUUID(),
                journalId,
                sourceAccount.getId(),
                EntryDirection.DEBIT,
                command.amount(),
                sourceResult.balanceAfter(),
                sourceResult.sequence(),
                now
        );
        Entry destEntry = new Entry(
                UUID.randomUUID(),
                journalId,
                destAccount.getId(),
                EntryDirection.CREDIT,
                command.amount(),
                destResult.balanceAfter(),
                destResult.sequence(),
                now
        );

        return PostingEntriesResult.success(List.of(sourceEntry, destEntry));
    }

    @Override
    public String getJournalDescription(PostJournalCommand command) {
        return DESCRIPTION;
    }
}
