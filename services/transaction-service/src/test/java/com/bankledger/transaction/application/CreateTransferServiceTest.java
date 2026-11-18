package com.bankledger.transaction.application;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.transaction.application.port.in.CreateTransferCommand;
import com.bankledger.transaction.application.port.out.AccountLimitRepositoryPort;
import com.bankledger.transaction.application.port.out.OutboxPort;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.application.service.CreateTransferService;
import com.bankledger.transaction.domain.command.PostJournalCommand;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import com.bankledger.transaction.domain.model.TransactionStatus;
import com.bankledger.transaction.domain.model.TransactionType;
import com.bankledger.transaction.domain.service.BankingProtocolGenerator;
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
class CreateTransferServiceTest {

    @Mock
    private TransactionRepositoryPort transactionRepositoryPort;

    @Mock
    private AccountLimitRepositoryPort accountLimitRepositoryPort;

    @Mock
    private OutboxPort outboxPort;

    private ObjectMapper objectMapper;
    private Clock fixedClock;
    private Instant fixedInstant;
    private BankingProtocolGenerator protocolGenerator;

    private CreateTransferService service;

    private final UUID customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID sourceAccountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID destinationAccountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID correlationId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-11-13T20:30:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        objectMapper = JsonMapper.builder().build();
        protocolGenerator = new BankingProtocolGenerator(fixedClock);

        service = new CreateTransferService(
                transactionRepositoryPort,
                accountLimitRepositoryPort,
                protocolGenerator,
                outboxPort,
                objectMapper,
                fixedClock
        );
    }

    @Test
    @DisplayName("createTransfer: creates PENDING transaction and records PostJournalCommand in outbox atomically")
    void shouldCreateTransferAndRecordOutboxEvent() throws Exception {
        when(transactionRepositoryPort.save(any(FinancialTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateTransferCommand command = new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                50000L,
                "Transferência PIX/TED",
                "API",
                customerId,
                correlationId
        );

        FinancialTransaction result = service.createTransfer(command);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getAmount()).isEqualTo(50000L);
        assertThat(result.getSourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(result.getDestinationAccountId()).isEqualTo(destinationAccountId);
        assertThat(result.getRequestedBy()).isEqualTo(customerId);
        assertThat(result.getCorrelationId()).isEqualTo(correlationId);
        assertThat(result.getCreatedAt()).isEqualTo(fixedInstant);
        assertThat(result.getProtocolNumber()).isNotNull();
        assertThat(UUID.fromString(result.getProtocolNumber())).isNotNull();

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
        assertThat(outboxEvent.getMessageKey()).isEqualTo(sourceAccountId.toString());

        PostJournalCommand postCommand = objectMapper.readValue(outboxEvent.getPayload(), PostJournalCommand.class);
        assertThat(postCommand.transactionId()).isEqualTo(result.getId());
        assertThat(postCommand.type()).isEqualTo("TRANSFER");
        assertThat(postCommand.amount()).isEqualTo(50000L);
        assertThat(postCommand.currency()).isEqualTo(BankingConstants.DEFAULT_CURRENCY);
        assertThat(postCommand.sourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(postCommand.destinationAccountId()).isEqualTo(destinationAccountId);
        assertThat(postCommand.terminalId()).isEqualTo("API");
        assertThat(postCommand.correlationId()).isEqualTo(correlationId);
        assertThat(postCommand.timestamp()).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("createTransfer: throws when command is null")
    void shouldThrowWhenCommandIsNull() {
        assertThatThrownBy(() -> service.createTransfer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command must not be null");
    }
}
