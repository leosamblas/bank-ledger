package com.bankledger.transaction.application;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.transaction.application.port.in.CreateTransferCommand;
import com.bankledger.transaction.application.port.out.AccountLimitRepositoryPort;
import com.bankledger.transaction.application.port.out.OutboxPort;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.application.service.CreateTransferService;
import com.bankledger.transaction.domain.exception.DailyLimitExceededException;
import com.bankledger.transaction.domain.model.AccountLimit;
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
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dynamic Database Limit Validation & Canonical Banking Protocol Tests")
class CreateTransferWithDynamicLimitTest {

    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^PRT-\\d{8}-[0-9A-F]{6}$");

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
        fixedInstant = Instant.parse("2026-11-18T14:30:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("America/Sao_Paulo"));
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
    @DisplayName("Account with approved limit R$ 10.000,00 can transfer R$ 8.000,00 and rejects R$ 11.000,00")
    void shouldAllowTransferWithinCustomLimitAndRejectExceedingLimit() {
        long approvedLimit = 1_000_000L; // R$ 10.000,00
        when(accountLimitRepositoryPort.findByAccountId(sourceAccountId))
                .thenReturn(Optional.of(new AccountLimit(sourceAccountId, approvedLimit, "GERENTE_CONTA", fixedInstant)));

        // No previous transfers today
        when(transactionRepositoryPort.sumDailyTransfers(eq(sourceAccountId), any(Instant.class)))
                .thenReturn(0L);

        when(transactionRepositoryPort.save(any(FinancialTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // 1. Transfer R$ 8.000,00 (800.000 cents) -> Should succeed
        CreateTransferCommand command8k = new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                800_000L,
                "Transferência R$ 8k",
                "WEB",
                customerId,
                correlationId
        );

        FinancialTransaction result = service.createTransfer(command8k);

        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(800_000L);
        assertThat(result.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getProtocolNumber()).isNotNull();
        assertThat(result.getProtocolNumber()).matches(PROTOCOL_PATTERN);
        assertThat(result.getProtocolNumber()).startsWith("PRT-20261118-");

        // 2. Transfer R$ 11.000,00 (1.100.000 cents) -> Should reject with DailyLimitExceededException
        CreateTransferCommand command11k = new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                1_100_000L,
                "Transferência R$ 11k",
                "WEB",
                customerId,
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> service.createTransfer(command11k))
                .isInstanceOf(DailyLimitExceededException.class)
                .hasMessageContaining("limit exceeded")
                .satisfies(ex -> {
                    DailyLimitExceededException dex = (DailyLimitExceededException) ex;
                    assertThat(dex.getAccountId()).isEqualTo(sourceAccountId);
                    assertThat(dex.getApprovedLimitCents()).isEqualTo(1_000_000L);
                    assertThat(dex.getUsedTodayCents()).isEqualTo(0L);
                    assertThat(dex.getRequestedAmountCents()).isEqualTo(1_100_000L);
                });
    }

    @Test
    @DisplayName("Account with custom approved limit R$ 200.000,00 can transfer R$ 150.000,00 (proving static 50k limit is gone!)")
    void shouldAllowTransferAbove50kWhenAccountHasHighApprovedLimit() {
        long approvedLimit = 20_000_000L; // R$ 200.000,00
        when(accountLimitRepositoryPort.findByAccountId(sourceAccountId))
                .thenReturn(Optional.of(new AccountLimit(sourceAccountId, approvedLimit, "DIRETORIA_RISCO", fixedInstant)));

        when(transactionRepositoryPort.sumDailyTransfers(eq(sourceAccountId), any(Instant.class)))
                .thenReturn(0L);

        when(transactionRepositoryPort.save(any(FinancialTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Transfer R$ 150.000,00 (15.000.000 cents)
        CreateTransferCommand command150k = new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                15_000_000L,
                "Transferência empresarial R$ 150k",
                "API",
                customerId,
                correlationId
        );

        FinancialTransaction result = service.createTransfer(command150k);

        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(15_000_000L);
        assertThat(result.getProtocolNumber()).matches(PROTOCOL_PATTERN);
        assertThat(result.getProtocolNumber()).startsWith("PRT-20261118-");

        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepositoryPort).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getProtocolNumber()).isEqualTo(result.getProtocolNumber());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxPort).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo(KafkaTopics.LEDGER_COMMANDS_V1);
    }

    @Test
    @DisplayName("Cumulative daily transfers are checked: existing used sum + current amount > limit throws exception")
    void shouldRejectWhenCumulativeTransfersExceedLimit() {
        long approvedLimit = 1_000_000L; // R$ 10.000,00
        when(accountLimitRepositoryPort.findByAccountId(sourceAccountId))
                .thenReturn(Optional.of(new AccountLimit(sourceAccountId, approvedLimit, "GERENTE", fixedInstant)));

        // Already transferred R$ 3.000,00 (300.000 cents) today
        when(transactionRepositoryPort.sumDailyTransfers(eq(sourceAccountId), any(Instant.class)))
                .thenReturn(300_000L);

        // Attempting to transfer R$ 8.000,00 (800.000 cents) -> 300k + 800k = 1.100k > 1.000k limit
        CreateTransferCommand command8k = new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                800_000L,
                "Transferência cumulativa excedendo limite",
                "APP",
                customerId,
                correlationId
        );

        assertThatThrownBy(() -> service.createTransfer(command8k))
                .isInstanceOf(DailyLimitExceededException.class)
                .satisfies(ex -> {
                    DailyLimitExceededException dex = (DailyLimitExceededException) ex;
                    assertThat(dex.getApprovedLimitCents()).isEqualTo(1_000_000L);
                    assertThat(dex.getUsedTodayCents()).isEqualTo(300_000L);
                    assertThat(dex.getRequestedAmountCents()).isEqualTo(800_000L);
                });

        verify(transactionRepositoryPort, never()).save(any());
        verify(outboxPort, never()).save(any());
    }

    @Test
    @DisplayName("Fallback to default limit R$ 50.000,00 when account has no explicit record in account_limit")
    void shouldFallbackToDefaultDailyLimitWhenNoConfigurationExists() {
        when(accountLimitRepositoryPort.findByAccountId(sourceAccountId))
                .thenReturn(Optional.empty()); // No record in database

        when(transactionRepositoryPort.sumDailyTransfers(eq(sourceAccountId), any(Instant.class)))
                .thenReturn(0L);

        // Attempt transfer exceeding default 50k: 5.000.001 cents
        CreateTransferCommand excessCommand = new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                5_000_001L,
                "Transferência acima do default",
                "WEB",
                customerId,
                correlationId
        );

        assertThatThrownBy(() -> service.createTransfer(excessCommand))
                .isInstanceOf(DailyLimitExceededException.class)
                .satisfies(ex -> {
                    DailyLimitExceededException dex = (DailyLimitExceededException) ex;
                    assertThat(dex.getApprovedLimitCents()).isEqualTo(5_000_000L);
                    assertThat(dex.getUsedTodayCents()).isEqualTo(0L);
                    assertThat(dex.getRequestedAmountCents()).isEqualTo(5_000_001L);
                });
    }
}
