package com.bankledger.ledger.application.service.strategy;

import com.bankledger.ledger.application.service.CashShardResolver;
import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryApplicationResult;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.LedgerAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Strategy handling DEPOSIT transactions.
 * Debits the system cash shard (Asset increase) and credits the customer account (Liability increase).
 */
@Component
@RequiredArgsConstructor
public class DepositPostingStrategy implements JournalPostingStrategy {

    public static final String TRANSACTION_TYPE = "DEPOSIT";
    public static final String DESCRIPTION = "Depósito contábil";

    private final CashShardResolver cashShardResolver;

    @Override
    public boolean supports(String transactionType) {
        return TRANSACTION_TYPE.equalsIgnoreCase(transactionType);
    }

    @Override
    public PostingAccountsPair resolveAccountsToLock(PostJournalCommand command) {
        if (command.destinationAccountId() == null) {
            throw new IllegalArgumentException("Destination account ID is required for deposit");
        }
        UUID cashShardId = cashShardResolver.resolveShardId(command.terminalId(), command.transactionId());
        UUID customerAccountId = command.destinationAccountId();
        return new PostingAccountsPair(cashShardId, customerAccountId);
    }

    @Override
    public PostingEntriesResult generateEntries(
            PostJournalCommand command,
            LedgerAccount cashAccount,
            LedgerAccount customerAccount,
            UUID journalId,
            Instant now
    ) {
        // Debit cash shard (Asset increases), Credit customer (Liability to customer increases)
        EntryApplicationResult cashResult = cashAccount.applyEntry(EntryDirection.DEBIT, command.amount());
        EntryApplicationResult customerResult = customerAccount.applyEntry(EntryDirection.CREDIT, command.amount());

        Entry cashEntry = new Entry(
                UUID.randomUUID(),
                journalId,
                cashAccount.getId(),
                EntryDirection.DEBIT,
                command.amount(),
                cashResult.balanceAfter(),
                cashResult.sequence(),
                now
        );
        Entry customerEntry = new Entry(
                UUID.randomUUID(),
                journalId,
                customerAccount.getId(),
                EntryDirection.CREDIT,
                command.amount(),
                customerResult.balanceAfter(),
                customerResult.sequence(),
                now
        );

        return PostingEntriesResult.success(List.of(cashEntry, customerEntry));
    }

    @Override
    public String getJournalDescription(PostJournalCommand command) {
        return DESCRIPTION;
    }
}
