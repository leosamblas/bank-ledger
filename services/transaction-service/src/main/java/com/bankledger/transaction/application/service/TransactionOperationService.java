package com.bankledger.transaction.application.service;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.transaction.application.port.in.CreateDepositCommand;
import com.bankledger.transaction.application.port.in.CreateDepositUseCase;
import com.bankledger.transaction.application.port.in.CreateWithdrawalCommand;
import com.bankledger.transaction.application.port.in.CreateWithdrawalUseCase;
import com.bankledger.transaction.application.port.out.OutboxPort;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.domain.command.PostJournalCommand;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core application service executing deposit and withdrawal transaction creation atomically.
 * Persists the FinancialTransaction with PENDING status and enqueues a PostJournalCommand
 * into the transactional Outbox for asynchronous processing by the ledger engine.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TransactionOperationService implements CreateDepositUseCase, CreateWithdrawalUseCase {

    public static final String TOPIC_LEDGER_COMMANDS = "ledger.commands.v1";
    public static final String AGGREGATE_TYPE = "FinancialTransaction";
    public static final String EVENT_TYPE_POST_JOURNAL = "PostJournalCommand";

    private final TransactionRepositoryPort transactionRepositoryPort;
    private final OutboxPort outboxPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public FinancialTransaction createDeposit(CreateDepositCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Instant now = Instant.now(clock);
        UUID txId = UUID.randomUUID();

        FinancialTransaction tx = FinancialTransaction.createDeposit(
                txId,
                command.destinationAccountId(),
                command.amount(),
                command.description(),
                command.requestedBy(),
                command.correlationId(),
                now
        );

        FinancialTransaction savedTx = transactionRepositoryPort.save(tx);

        PostJournalCommand postCommand = new PostJournalCommand(
                UUID.randomUUID(),
                savedTx.getId(),
                "DEPOSIT",
                savedTx.getAmount(),
                savedTx.getCurrency(),
                null,
                command.destinationAccountId(),
                command.terminalId(),
                command.correlationId(),
                now
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(postCommand);
        } catch (Exception e) {
            log.error("Failed to serialize PostJournalCommand for deposit txId={}", savedTx.getId(), e);
            throw new IllegalStateException("Failed to serialize PostJournalCommand to JSON", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.pending(
                AGGREGATE_TYPE,
                savedTx.getId(),
                EVENT_TYPE_POST_JOURNAL,
                TOPIC_LEDGER_COMMANDS,
                command.destinationAccountId().toString(),
                payload
        );

        outboxPort.save(outboxEvent);

        log.info("Deposit created successfully: txId={}, destinationAccountId={}, amount={}",
                savedTx.getId(), command.destinationAccountId(), command.amount());

        return savedTx;
    }

    @Override
    public FinancialTransaction createWithdrawal(CreateWithdrawalCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Instant now = Instant.now(clock);
        UUID txId = UUID.randomUUID();

        FinancialTransaction tx = FinancialTransaction.createWithdrawal(
                txId,
                command.sourceAccountId(),
                command.amount(),
                command.description(),
                command.requestedBy(),
                command.correlationId(),
                now
        );

        FinancialTransaction savedTx = transactionRepositoryPort.save(tx);

        PostJournalCommand postCommand = new PostJournalCommand(
                UUID.randomUUID(),
                savedTx.getId(),
                "WITHDRAWAL",
                savedTx.getAmount(),
                savedTx.getCurrency(),
                command.sourceAccountId(),
                null,
                command.terminalId(),
                command.correlationId(),
                now
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(postCommand);
        } catch (Exception e) {
            log.error("Failed to serialize PostJournalCommand for withdrawal txId={}", savedTx.getId(), e);
            throw new IllegalStateException("Failed to serialize PostJournalCommand to JSON", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.pending(
                AGGREGATE_TYPE,
                savedTx.getId(),
                EVENT_TYPE_POST_JOURNAL,
                TOPIC_LEDGER_COMMANDS,
                command.sourceAccountId().toString(),
                payload
        );

        outboxPort.save(outboxEvent);

        log.info("Withdrawal created successfully: txId={}, sourceAccountId={}, amount={}",
                savedTx.getId(), command.sourceAccountId(), command.amount());

        return savedTx;
    }
}
