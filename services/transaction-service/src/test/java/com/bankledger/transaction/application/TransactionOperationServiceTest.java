package com.bankledger.transaction.application;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.transaction.application.port.in.CreateDepositCommand;
import com.bankledger.transaction.application.port.in.CreateWithdrawalCommand;
import com.bankledger.transaction.application.port.out.OutboxPort;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.application.service.TransactionOperationService;
import com.bankledger.transaction.domain.command.PostJournalCommand;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import com.bankledger.transaction.domain.model.TransactionStatus;
import com.bankledger.transaction.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionOperationServiceTest {

    @Mock
    private TransactionRepositoryPort transactionRepositoryPort;

    @Mock
    private OutboxPort outboxPort;

    private ObjectMapper objectMapper;
    private Clock fixedClock;
    private Instant fixedInstant;

    private TransactionOperationService service;

    private final UUID customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID accountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID correlationId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-11-13T20:30:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        objectMapper = JsonMapper.builder().build();

        service = new TransactionOperationService(
                transactionRepositoryPort,
                outboxPort,
                objectMapper,
                fixedClock
        );
    }

    @Test
    @DisplayName("createDeposit: creates PENDING transaction and records PostJournalCommand in outbox atomically")
    void shouldCreateDepositAndRecordOutboxEvent() throws Exception {
        when(transactionRepositoryPort.save(any(FinancialTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateDepositCommand command = new CreateDepositCommand(
                accountId,
                150000L,
                "Depósito em conta corrente",
                "TERM-01",
                customerId,
                correlationId
        );

        FinancialTransaction result = service.createDeposit(command);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getAmount()).isEqualTo(150000L);
        assertThat(result.getDestinationAccountId()).isEqualTo(accountId);
        assertThat(result.getSourceAccountId()).isNull();
        assertThat(result.getRequestedBy()).isEqualTo(customerId);
        assertThat(result.getCorrelationId()).isEqualTo(correlationId);
        assertThat(result.getCreatedAt()).isEqualTo(fixedInstant);

        // Verify transaction saved
        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepositoryPort).save(txCaptor.capture());
        FinancialTransaction savedTx = txCaptor.getValue();
        assertThat(savedTx.getId()).isEqualTo(result.getId());

        // Verify outbox event saved
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxPort).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();

        assertThat(outboxEvent.getAggregateType()).isEqualTo("FinancialTransaction");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(result.getId());
        assertThat(outboxEvent.getEventType()).isEqualTo("PostJournalCommand");
        assertThat(outboxEvent.getTopic()).isEqualTo(KafkaTopics.LEDGER_COMMANDS_V1);
        assertThat(outboxEvent.getMessageKey()).isEqualTo(accountId.toString());

        PostJournalCommand publishedCommand = objectMapper.readValue(outboxEvent.getPayload(), PostJournalCommand.class);
        assertThat(publishedCommand.transactionId()).isEqualTo(result.getId());
        assertThat(publishedCommand.type()).isEqualTo("DEPOSIT");
        assertThat(publishedCommand.amount()).isEqualTo(150000L);
        assertThat(publishedCommand.currency()).isEqualTo(BankingConstants.DEFAULT_CURRENCY);
        assertThat(publishedCommand.sourceAccountId()).isNull();
        assertThat(publishedCommand.destinationAccountId()).isEqualTo(accountId);
        assertThat(publishedCommand.terminalId()).isEqualTo("TERM-01");
        assertThat(publishedCommand.correlationId()).isEqualTo(correlationId);
        assertThat(publishedCommand.timestamp()).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("createWithdrawal: creates PENDING transaction and records PostJournalCommand in outbox atomically")
    void shouldCreateWithdrawalAndRecordOutboxEvent() throws Exception {
        when(transactionRepositoryPort.save(any(FinancialTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateWithdrawalCommand command = new CreateWithdrawalCommand(
                accountId,
                50000L,
                "Saque caixa 24h",
                "ATM-99",
                customerId,
                correlationId
        );

        FinancialTransaction result = service.createWithdrawal(command);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getAmount()).isEqualTo(50000L);
        assertThat(result.getSourceAccountId()).isEqualTo(accountId);
        assertThat(result.getDestinationAccountId()).isNull();
        assertThat(result.getRequestedBy()).isEqualTo(customerId);
        assertThat(result.getCorrelationId()).isEqualTo(correlationId);
        assertThat(result.getCreatedAt()).isEqualTo(fixedInstant);

        // Verify transaction saved
        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepositoryPort).save(txCaptor.capture());
        FinancialTransaction savedTx = txCaptor.getValue();
        assertThat(savedTx.getId()).isEqualTo(result.getId());

        // Verify outbox event saved
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxPort).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();

        assertThat(outboxEvent.getAggregateType()).isEqualTo("FinancialTransaction");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(result.getId());
        assertThat(outboxEvent.getEventType()).isEqualTo("PostJournalCommand");
        assertThat(outboxEvent.getTopic()).isEqualTo(KafkaTopics.LEDGER_COMMANDS_V1);
        assertThat(outboxEvent.getMessageKey()).isEqualTo(accountId.toString());

        PostJournalCommand publishedCommand = objectMapper.readValue(outboxEvent.getPayload(), PostJournalCommand.class);
        assertThat(publishedCommand.transactionId()).isEqualTo(result.getId());
        assertThat(publishedCommand.type()).isEqualTo("WITHDRAWAL");
        assertThat(publishedCommand.amount()).isEqualTo(50000L);
        assertThat(publishedCommand.currency()).isEqualTo(BankingConstants.DEFAULT_CURRENCY);
        assertThat(publishedCommand.sourceAccountId()).isEqualTo(accountId);
        assertThat(publishedCommand.destinationAccountId()).isNull();
        assertThat(publishedCommand.terminalId()).isEqualTo("ATM-99");
        assertThat(publishedCommand.correlationId()).isEqualTo(correlationId);
        assertThat(publishedCommand.timestamp()).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("createDeposit throws NullPointerException when command is null")
    void shouldThrowWhenDepositCommandIsNull() {
        assertThatThrownBy(() -> service.createDeposit(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command must not be null");
    }

    @Test
    @DisplayName("createWithdrawal throws NullPointerException when command is null")
    void shouldThrowWhenWithdrawalCommandIsNull() {
        assertThatThrownBy(() -> service.createWithdrawal(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command must not be null");
    }
}
