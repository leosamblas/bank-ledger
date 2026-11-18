package com.bankledger.transaction.application.service;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.transaction.application.port.in.CreateTransferCommand;
import com.bankledger.transaction.application.port.in.CreateTransferUseCase;
import com.bankledger.transaction.application.port.out.AccountLimitRepositoryPort;
import com.bankledger.transaction.application.port.out.OutboxPort;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.domain.command.PostJournalCommand;
import com.bankledger.transaction.domain.exception.DailyLimitExceededException;
import com.bankledger.transaction.domain.model.AccountLimit;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import com.bankledger.transaction.domain.service.BankingProtocolGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Core application service executing transfer transaction creation atomically.
 * Validates dynamic daily database limits, generates canonical banking protocol,
 * persists the FinancialTransaction with PENDING status and enqueues a PostJournalCommand
 * into the transactional Outbox for asynchronous processing by the ledger engine.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CreateTransferService implements CreateTransferUseCase {

    public static final String TOPIC_LEDGER_COMMANDS = KafkaTopics.LEDGER_COMMANDS_V1;
    public static final String AGGREGATE_TYPE = "FinancialTransaction";
    public static final String EVENT_TYPE_POST_JOURNAL = "PostJournalCommand";
    public static final ZoneId ZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final TransactionRepositoryPort transactionRepositoryPort;
    private final AccountLimitRepositoryPort accountLimitRepositoryPort;
    private final BankingProtocolGenerator protocolGenerator;
    private final OutboxPort outboxPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public FinancialTransaction createTransfer(CreateTransferCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Instant now = Instant.now(clock);

        // 1. Retrieve account's approved daily limit from database (fallback to 5_000_000 cents if not configured)
        long approvedLimit = (accountLimitRepositoryPort != null)
                ? accountLimitRepositoryPort.findByAccountId(command.sourceAccountId())
                        .map(AccountLimit::dailyTransferLimitCents)
                        .orElse(AccountLimit.DEFAULT_DAILY_TRANSFER_LIMIT_CENTS)
                : AccountLimit.DEFAULT_DAILY_TRANSFER_LIMIT_CENTS;

        // 2. Query the sum of transactions today in America/Sao_Paulo (UTC-3)
        Instant startOfDay = now.atZone(ZONE_SAO_PAULO)
                .toLocalDate()
                .atStartOfDay(ZONE_SAO_PAULO)
                .toInstant();

        long usedToday = transactionRepositoryPort.sumDailyTransfers(command.sourceAccountId(), startOfDay);

        // 3. Dynamic limit check
        if (usedToday + command.amount() > approvedLimit) {
            log.warn("Daily transfer limit exceeded for account {}: requested={}, usedToday={}, approvedLimit={}",
                    command.sourceAccountId(), command.amount(), usedToday, approvedLimit);
            throw new DailyLimitExceededException(
                    command.sourceAccountId(),
                    approvedLimit,
                    usedToday,
                    command.amount()
            );
        }

        UUID txId = UUID.randomUUID();

        // 4. Generate canonical banking protocol seeded with transaction ID to prevent collisions
        String protocolNumber = protocolGenerator.generate(now.atZone(ZONE_SAO_PAULO).toLocalDate(), txId);

        FinancialTransaction tx = FinancialTransaction.createTransfer(
                txId,
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.amount(),
                command.description(),
                command.requestedBy(),
                command.correlationId(),
                protocolNumber,
                now
        );

        FinancialTransaction savedTx = transactionRepositoryPort.save(tx);

        PostJournalCommand postCommand = new PostJournalCommand(
                UUID.randomUUID(),
                savedTx.getId(),
                "TRANSFER",
                savedTx.getAmount(),
                savedTx.getCurrency(),
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.terminalId(),
                command.correlationId(),
                now,
                command.description(),
                protocolNumber,
                command.destinationHolderName(),
                command.destinationDocument(),
                command.destinationAccount()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(postCommand);
        } catch (Exception e) {
            log.error("Failed to serialize PostJournalCommand for transfer txId={}", savedTx.getId(), e);
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

        log.info("Transfer created successfully: txId={}, protocol={}, sourceAccountId={}, destinationAccountId={}, amount={}",
                savedTx.getId(), protocolNumber, command.sourceAccountId(), command.destinationAccountId(), command.amount());

        return savedTx;
    }
}
