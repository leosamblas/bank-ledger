package com.bankledger.ledger.application.service;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.ledger.application.port.in.PostJournalUseCase;
import com.bankledger.ledger.application.port.out.JournalRepositoryPort;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.application.port.out.OutboxPort;
import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.event.EntryPostedEvent;
import com.bankledger.ledger.domain.event.JournalPostedEvent;
import com.bankledger.ledger.domain.event.JournalRejectedEvent;
import com.bankledger.ledger.domain.exception.InsufficientFundsException;
import com.bankledger.ledger.domain.exception.LedgerAccountNotFoundException;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryApplicationResult;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.LedgerAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Core application service executing double-entry bookkeeping transactions atomically.
 * Enforces pessimistic locking ordered by account UUID to prevent deadlocks (ADR 0002),
 * checks idempotency, evaluates balance constraints, persists journal & entries,
 * and publishes events to transactional outbox.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PostJournalService implements PostJournalUseCase {

    private final LedgerAccountRepositoryPort ledgerAccountRepositoryPort;
    private final JournalRepositoryPort journalRepositoryPort;
    private final OutboxPort outboxPort;
    private final CashShardResolver cashShardResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public Journal postJournal(PostJournalCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // 1. Idempotency check: if transaction was already processed, return existing journal
        Optional<Journal> existing = journalRepositoryPort.findByTransactionId(command.transactionId());
        if (existing.isPresent()) {
            log.info("Idempotency hit: journal already recorded for transactionId={}", command.transactionId());
            return existing.get();
        }

        String type = command.type().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "DEPOSIT" -> processDeposit(command);
            case "WITHDRAWAL" -> processWithdrawal(command);
            case "TRANSFER" -> processTransfer(command);
            default -> throw new IllegalArgumentException("Unsupported transaction type: " + command.type());
        };
    }

    private Journal processDeposit(PostJournalCommand command) {
        if (command.destinationAccountId() == null) {
            throw new IllegalArgumentException("Destination account ID is required for deposit");
        }
        UUID cashShardId = cashShardResolver.resolveShardId(command.terminalId(), command.transactionId());
        UUID customerAccountId = command.destinationAccountId();

        LockedAccounts locked = lockAccountsInOrder(cashShardId, customerAccountId);
        LedgerAccount cashAccount = locked.accountA();
        LedgerAccount customerAccount = locked.accountB();

        Instant now = Instant.now(clock);
        UUID journalId = UUID.randomUUID();

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
        List<Entry> entries = List.of(cashEntry, customerEntry);

        Journal journal = Journal.createPosted(
                journalId,
                command.transactionId(),
                command.type(),
                "Depósito contábil",
                command.amount(),
                command.currency(),
                command.correlationId(),
                now,
                entries
        );

        ledgerAccountRepositoryPort.save(cashAccount);
        ledgerAccountRepositoryPort.save(customerAccount);
        Journal savedJournal = journalRepositoryPort.save(journal);

        publishPostedOutboxEvents(savedJournal, entries);

        return savedJournal;
    }

    private Journal processWithdrawal(PostJournalCommand command) {
        if (command.sourceAccountId() == null) {
            throw new IllegalArgumentException("Source account ID is required for withdrawal");
        }
        UUID customerAccountId = command.sourceAccountId();
        UUID cashShardId = cashShardResolver.resolveShardId(command.terminalId(), command.transactionId());

        LockedAccounts locked = lockAccountsInOrder(customerAccountId, cashShardId);
        LedgerAccount customerAccount = locked.accountA();
        LedgerAccount cashAccount = locked.accountB();

        Instant now = Instant.now(clock);

        EntryApplicationResult customerResult;
        try {
            customerResult = customerAccount.applyEntry(EntryDirection.DEBIT, command.amount());
        } catch (InsufficientFundsException ex) {
            log.warn("Withdrawal rejected due to insufficient funds: transactionId={}, accountId={}",
                    command.transactionId(), customerAccountId);
            return handleInsufficientFunds(command, now, "Saldo insuficiente para efetuar o saque.");
        }

        EntryApplicationResult cashResult = cashAccount.applyEntry(EntryDirection.CREDIT, command.amount());

        UUID journalId = UUID.randomUUID();
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
        List<Entry> entries = List.of(customerEntry, cashEntry);

        Journal journal = Journal.createPosted(
                journalId,
                command.transactionId(),
                command.type(),
                "Saque contábil",
                command.amount(),
                command.currency(),
                command.correlationId(),
                now,
                entries
        );

        ledgerAccountRepositoryPort.save(customerAccount);
        ledgerAccountRepositoryPort.save(cashAccount);
        Journal savedJournal = journalRepositoryPort.save(journal);

        publishPostedOutboxEvents(savedJournal, entries);

        return savedJournal;
    }

    private Journal processTransfer(PostJournalCommand command) {
        if (command.sourceAccountId() == null) {
            throw new IllegalArgumentException("Source account ID is required for transfer");
        }
        if (command.destinationAccountId() == null) {
            throw new IllegalArgumentException("Destination account ID is required for transfer");
        }
        UUID sourceAccountId = command.sourceAccountId();
        UUID destAccountId = command.destinationAccountId();

        LockedAccounts locked = lockAccountsInOrder(sourceAccountId, destAccountId);
        LedgerAccount sourceAccount = locked.accountA();
        LedgerAccount destAccount = locked.accountB();

        Instant now = Instant.now(clock);

        EntryApplicationResult sourceResult;
        try {
            sourceResult = sourceAccount.applyEntry(EntryDirection.DEBIT, command.amount());
        } catch (InsufficientFundsException ex) {
            log.warn("Transfer rejected due to insufficient funds: transactionId={}, accountId={}",
                    command.transactionId(), sourceAccountId);
            return handleInsufficientFunds(command, now, "Saldo insuficiente para efetuar a transferência.");
        }

        EntryApplicationResult destResult = destAccount.applyEntry(EntryDirection.CREDIT, command.amount());

        UUID journalId = UUID.randomUUID();
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
        List<Entry> entries = List.of(sourceEntry, destEntry);

        Journal journal = Journal.createPosted(
                journalId,
                command.transactionId(),
                command.type(),
                "Transferência contábil",
                command.amount(),
                command.currency(),
                command.correlationId(),
                now,
                entries
        );

        ledgerAccountRepositoryPort.save(sourceAccount);
        ledgerAccountRepositoryPort.save(destAccount);
        Journal savedJournal = journalRepositoryPort.save(journal);

        publishPostedOutboxEvents(savedJournal, entries);

        return savedJournal;
    }

    private Journal handleInsufficientFunds(PostJournalCommand command, Instant now, String rejectReason) {
        UUID journalId = UUID.randomUUID();
        Journal rejectedJournal = Journal.createRejected(
                journalId,
                command.transactionId(),
                command.type(),
                "Transação rejeitada: " + rejectReason,
                command.amount(),
                command.currency(),
                command.correlationId(),
                now,
                "INSUFFICIENT_FUNDS",
                rejectReason
        );

        Journal savedJournal = journalRepositoryPort.save(rejectedJournal);

        JournalRejectedEvent rejectedEvent = new JournalRejectedEvent(
                savedJournal.getTransactionId(),
                savedJournal.getType(),
                savedJournal.getStatus(),
                savedJournal.getRejectCode(),
                savedJournal.getRejectReason(),
                savedJournal.getCorrelationId(),
                now
        );

        OutboxEvent outboxEvent = OutboxEvent.pending(
                "Journal",
                savedJournal.getId(),
                JournalRejectedEvent.EVENT_TYPE,
                JournalRejectedEvent.TOPIC,
                savedJournal.getTransactionId().toString(),
                toJson(rejectedEvent)
        );

        outboxPort.save(outboxEvent);

        return savedJournal;
    }

    private void publishPostedOutboxEvents(Journal journal, List<Entry> entries) {
        JournalPostedEvent postedEvent = new JournalPostedEvent(
                journal.getId(),
                journal.getTransactionId(),
                journal.getType(),
                journal.getStatus(),
                journal.getTotalAmount(),
                journal.getCurrency(),
                journal.getCorrelationId(),
                journal.getPostedAt()
        );

        OutboxEvent journalOutbox = OutboxEvent.pending(
                "Journal",
                journal.getId(),
                JournalPostedEvent.EVENT_TYPE,
                JournalPostedEvent.TOPIC,
                journal.getTransactionId().toString(),
                toJson(postedEvent)
        );

        List<OutboxEvent> outboxEvents = new ArrayList<>();
        outboxEvents.add(journalOutbox);

        for (Entry entry : entries) {
            EntryPostedEvent entryEvent = new EntryPostedEvent(
                    entry.id(),
                    entry.journalId(),
                    journal.getTransactionId(),
                    entry.ledgerAccountId(),
                    entry.direction(),
                    entry.amount(),
                    entry.balanceAfter(),
                    entry.sequence(),
                    entry.createdAt()
            );
            OutboxEvent entryOutbox = OutboxEvent.pending(
                    "Entry",
                    entry.id(),
                    EntryPostedEvent.EVENT_TYPE,
                    EntryPostedEvent.TOPIC,
                    entry.ledgerAccountId().toString(),
                    toJson(entryEvent)
            );
            outboxEvents.add(entryOutbox);
        }

        outboxPort.saveAll(outboxEvents);
    }

    /**
     * Acquires pessimistic locks on two accounts in deterministic lexicographical UUID order (ADR 0002).
     * Prevents deadlocks under high concurrency.
     */
    private LockedAccounts lockAccountsInOrder(UUID idA, UUID idB) {
        if (Objects.equals(idA, idB)) {
            throw new IllegalArgumentException("Source and destination accounts must be distinct: " + idA);
        }

        UUID firstId = idA.compareTo(idB) <= 0 ? idA : idB;
        UUID secondId = idA.compareTo(idB) <= 0 ? idB : idA;

        LedgerAccount firstAcc = ledgerAccountRepositoryPort.findByIdForUpdate(firstId)
                .orElseThrow(() -> new LedgerAccountNotFoundException(firstId));
        LedgerAccount secondAcc = ledgerAccountRepositoryPort.findByIdForUpdate(secondId)
                .orElseThrow(() -> new LedgerAccountNotFoundException(secondId));

        LedgerAccount accountA = firstAcc.getId().equals(idA) ? firstAcc : secondAcc;
        LedgerAccount accountB = firstAcc.getId().equals(idB) ? firstAcc : secondAcc;

        return new LockedAccounts(accountA, accountB);
    }

    private record LockedAccounts(LedgerAccount accountA, LedgerAccount accountB) {}

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize " + value.getClass().getSimpleName() + " to JSON", e);
        }
    }
}
