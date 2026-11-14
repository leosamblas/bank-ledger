package com.bankledger.ledger.application.service.strategy;

import com.bankledger.ledger.application.service.CashShardResolver;
import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.exception.InsufficientFundsException;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryApplicationResult;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.LedgerAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Strategy handling WITHDRAWAL transactions.
 * Debits the customer account (Liability decrease) and credits the system cash shard (Asset decrease).
 * Catches insufficient funds and yields rejection result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalPostingStrategy implements JournalPostingStrategy {

    public static final String TRANSACTION_TYPE = "WITHDRAWAL";
    public static final String DESCRIPTION = "Saque contábil";
    public static final String REJECTION_CODE = "INSUFFICIENT_FUNDS";
    public static final String REJECTION_REASON = "Saldo insuficiente para efetuar o saque.";

    private final CashShardResolver cashShardResolver;

    @Override
    public boolean supports(String transactionType) {
        return TRANSACTION_TYPE.equalsIgnoreCase(transactionType);
    }

    @Override
    public PostingAccountsPair resolveAccountsToLock(PostJournalCommand command) {
        if (command.sourceAccountId() == null) {
            throw new IllegalArgumentException("Source account ID is required for withdrawal");
        }
        UUID customerAccountId = command.sourceAccountId();
        UUID cashShardId = cashShardResolver.resolveShardId(command.terminalId(), command.transactionId());
        return new PostingAccountsPair(customerAccountId, cashShardId);
    }

    @Override
    public PostingEntriesResult generateEntries(
            PostJournalCommand command,
            LedgerAccount customerAccount,
            LedgerAccount cashAccount,
            UUID journalId,
            Instant now
    ) {
        EntryApplicationResult customerResult;
        try {
            customerResult = customerAccount.applyEntry(EntryDirection.DEBIT, command.amount());
        } catch (InsufficientFundsException ex) {
            log.warn("Withdrawal rejected due to insufficient funds: transactionId={}, accountId={}",
                    command.transactionId(), customerAccount.getId());
            return PostingEntriesResult.rejected(REJECTION_CODE, REJECTION_REASON);
        }

        EntryApplicationResult cashResult = cashAccount.applyEntry(EntryDirection.CREDIT, command.amount());

        Entry customerEntry = new Entry(
                UUID.randomUUID(),
                journalId,
                customerAccount.getId(),
                EntryDirection.DEBIT,
                command.amount(),
                customerResult.balanceAfter(),
                customerResult.sequence(),
                now
        );
        Entry cashEntry = new Entry(
                UUID.randomUUID(),
                journalId,
                cashAccount.getId(),
                EntryDirection.CREDIT,
                command.amount(),
                cashResult.balanceAfter(),
                cashResult.sequence(),
                now
        );

        return PostingEntriesResult.success(List.of(customerEntry, cashEntry));
    }

    @Override
    public String getJournalDescription(PostJournalCommand command) {
        return DESCRIPTION;
    }
}
