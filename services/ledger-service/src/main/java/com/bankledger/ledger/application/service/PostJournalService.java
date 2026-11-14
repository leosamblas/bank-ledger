package com.bankledger.ledger.application.service;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.ledger.application.port.in.PostJournalUseCase;
import com.bankledger.ledger.application.port.out.JournalRepositoryPort;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.application.port.out.OutboxPort;
import com.bankledger.ledger.application.service.strategy.JournalPostingStrategy;
import com.bankledger.ledger.application.service.strategy.PostingAccountsPair;
import com.bankledger.ledger.application.service.strategy.PostingEntriesResult;
import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.event.EntryPostedEvent;
import com.bankledger.ledger.domain.event.JournalPostedEvent;
import com.bankledger.ledger.domain.event.JournalRejectedEvent;
import com.bankledger.ledger.domain.exception.LedgerAccountNotFoundException;
import com.bankledger.ledger.domain.model.Entry;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Core application service executing double-entry bookkeeping transactions atomically.
 * Orchestrates posting strategies for account resolution and entry generation,
 * enforces pessimistic locking ordered by account UUID to prevent deadlocks (ADR 0002),
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
    private final List<JournalPostingStrategy> strategies;
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

        // 2. Resolve matching strategy for transaction type
        JournalPostingStrategy strategy = strategies.stream()
                .filter(s -> s.supports(command.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported transaction type: " + command.type()));

        // 3. Resolve accounts to lock
        PostingAccountsPair accountsPair = strategy.resolveAccountsToLock(command);

        // 4. Pessimistic lock in deterministic UUID order (ADR 0002)
        LockedAccounts locked = lockAccountsInOrder(accountsPair.accountIdA(), accountsPair.accountIdB());

        Instant now = Instant.now(clock);
        UUID journalId = UUID.randomUUID();

        // 5. Generate entries via strategy
        PostingEntriesResult result = strategy.generateEntries(
                command,
                locked.accountA(),
                locked.accountB(),
                journalId,
                now
        );

        // 6. Handle rejection if strategy rejected transaction
        if (result.isRejected()) {
            return handleRejection(command, now, journalId, result.rejectionCode(), result.rejectionReason());
        }

        // 7. Persist updated accounts and balanced journal
        ledgerAccountRepositoryPort.save(locked.accountA());
        ledgerAccountRepositoryPort.save(locked.accountB());

        Journal journal = Journal.createPosted(
                journalId,
                command.transactionId(),
                command.type(),
                strategy.getJournalDescription(command),
                command.amount(),
                command.currency(),
                command.correlationId(),
                now,
                result.entries()
        );

        Journal savedJournal = journalRepositoryPort.save(journal);

        // 8. Publish outbox events (JournalPostedEvent & EntryPostedEvents)
        publishPostedOutboxEvents(savedJournal, result.entries());

        return savedJournal;
    }

    private Journal handleRejection(
            PostJournalCommand command,
            Instant now,
            UUID journalId,
            String rejectCode,
            String rejectReason
    ) {
        Journal rejectedJournal = Journal.createRejected(
                journalId,
                command.transactionId(),
                command.type(),
                "Transação rejeitada: " + rejectReason,
                command.amount(),
                command.currency(),
                command.correlationId(),
                now,
                rejectCode,
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
