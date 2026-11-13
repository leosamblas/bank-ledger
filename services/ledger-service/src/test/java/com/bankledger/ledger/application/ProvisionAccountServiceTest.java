package com.bankledger.ledger.application;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.application.port.out.OutboxPort;
import com.bankledger.ledger.application.service.ProvisionAccountService;
import com.bankledger.ledger.domain.event.AccountOpenedPayload;
import com.bankledger.ledger.domain.event.LedgerAccountProvisionedEvent;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProvisionAccountService Application Service Tests")
class ProvisionAccountServiceTest {

    @Mock
    private LedgerAccountRepositoryPort ledgerAccountRepositoryPort;

    @Mock
    private OutboxPort outboxPort;

    private ObjectMapper objectMapper;
    private Clock fixedClock;
    private ProvisionAccountService provisionAccountService;

    private static final Instant FIXED_NOW = Instant.parse("2026-11-13T18:30:00Z");

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        provisionAccountService = new ProvisionAccountService(
                ledgerAccountRepositoryPort,
                outboxPort,
                objectMapper,
                fixedClock
        );
    }

    @Test
    @DisplayName("Should provision new customer ledger account and publish outbox event")
    void shouldProvisionNewCustomerLedgerAccountSuccessfully() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-11-13T10:00:00Z");

        AccountOpenedPayload payload = new AccountOpenedPayload(
                accountId,
                customerId,
                "12345678900",
                "Maria da Silva",
                "0001",
                "100000",
                "1",
                openedAt
        );

        when(ledgerAccountRepositoryPort.findById(accountId)).thenReturn(Optional.empty());

        provisionAccountService.provisionAccount(payload);

        // Verify account persisted with correct values
        ArgumentCaptor<LedgerAccount> accountCaptor = ArgumentCaptor.forClass(LedgerAccount.class);
        verify(ledgerAccountRepositoryPort).save(accountCaptor.capture());
        LedgerAccount savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getId()).isEqualTo(accountId);
        assertThat(savedAccount.getCode()).isEqualTo("ACC-0001-100000-1");
        assertThat(savedAccount.getKind()).isEqualTo(AccountKind.CUSTOMER);
        assertThat(savedAccount.getNormalBalance()).isEqualTo(NormalBalance.CREDIT);
        assertThat(savedAccount.getCurrency()).isEqualTo("BRL");
        assertThat(savedAccount.getBalance()).isZero();
        assertThat(savedAccount.getLastSequence()).isZero();
        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savedAccount.getCreatedAt()).isEqualTo(openedAt);
        assertThat(savedAccount.getUpdatedAt()).isEqualTo(openedAt);
        assertThat(savedAccount.getVersion()).isZero();

        // Verify outbox event persisted
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxPort).save(outboxCaptor.capture());
        OutboxEvent savedOutbox = outboxCaptor.getValue();

        assertThat(savedOutbox.getAggregateType()).isEqualTo("LedgerAccount");
        assertThat(savedOutbox.getAggregateId()).isEqualTo(accountId);
        assertThat(savedOutbox.getEventType()).isEqualTo(LedgerAccountProvisionedEvent.EVENT_TYPE);
        assertThat(savedOutbox.getTopic()).isEqualTo(LedgerAccountProvisionedEvent.TOPIC);
        assertThat(savedOutbox.getMessageKey()).isEqualTo(accountId.toString());

        LedgerAccountProvisionedEvent parsedEvent = objectMapper.readValue(
                savedOutbox.getPayload(),
                LedgerAccountProvisionedEvent.class
        );
        assertThat(parsedEvent.accountId()).isEqualTo(accountId);
        assertThat(parsedEvent.code()).isEqualTo("ACC-0001-100000-1");
        assertThat(parsedEvent.provisionedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("Should skip provisioning silently and idempotently if account already exists")
    void shouldSkipProvisioningSilentlyWhenAccountAlreadyExists() {
        UUID accountId = UUID.randomUUID();
        AccountOpenedPayload payload = new AccountOpenedPayload(
                accountId,
                UUID.randomUUID(),
                "12345678900",
                "Maria da Silva",
                "0001",
                "100000",
                "1",
                FIXED_NOW
        );

        LedgerAccount existing = LedgerAccount.createCustomerInitial(accountId, "ACC-0001-100000-1");
        when(ledgerAccountRepositoryPort.findById(accountId)).thenReturn(Optional.of(existing));

        provisionAccountService.provisionAccount(payload);

        verify(ledgerAccountRepositoryPort, never()).save(any());
        verify(outboxPort, never()).save(any());
    }

    @Test
    @DisplayName("Should fallback to clock now when openedAt is null in payload")
    void shouldFallbackToClockNowWhenOpenedAtIsNull() {
        UUID accountId = UUID.randomUUID();
        AccountOpenedPayload payload = new AccountOpenedPayload(
                accountId,
                null,
                null,
                null,
                "0001",
                "200000",
                "2",
                null
        );

        when(ledgerAccountRepositoryPort.findById(accountId)).thenReturn(Optional.empty());

        provisionAccountService.provisionAccount(payload);

        ArgumentCaptor<LedgerAccount> accountCaptor = ArgumentCaptor.forClass(LedgerAccount.class);
        verify(ledgerAccountRepositoryPort).save(accountCaptor.capture());
        LedgerAccount savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(savedAccount.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(savedAccount.getCode()).isEqualTo("ACC-0001-200000-2");
    }

    @Test
    @DisplayName("Should throw NullPointerException when payload is null")
    void shouldThrowExceptionWhenPayloadIsNull() {
        assertThatThrownBy(() -> provisionAccountService.provisionAccount(null))
                .isInstanceOf(NullPointerException.class);
    }
}
