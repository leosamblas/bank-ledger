package com.bankledger.ledger.application;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.ledger.application.port.out.JournalRepositoryPort;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.application.port.out.OutboxPort;
import com.bankledger.ledger.application.service.CashShardResolver;
import com.bankledger.ledger.application.service.PostJournalService;
import com.bankledger.ledger.application.service.strategy.DepositPostingStrategy;
import com.bankledger.ledger.application.service.strategy.JournalPostingStrategy;
import com.bankledger.ledger.application.service.strategy.TransferPostingStrategy;
import com.bankledger.ledger.application.service.strategy.WithdrawalPostingStrategy;
import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.event.EntryPostedEvent;
import com.bankledger.ledger.domain.event.JournalPostedEvent;
import com.bankledger.ledger.domain.event.JournalRejectedEvent;
import com.bankledger.ledger.domain.exception.LedgerAccountNotFoundException;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.JournalStatus;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostJournalService Application Service Tests")
class PostJournalServiceTest {

    @Mock
    private LedgerAccountRepositoryPort ledgerAccountRepositoryPort;

    @Mock
    private JournalRepositoryPort journalRepositoryPort;

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private CashShardResolver cashShardResolver;

    private ObjectMapper objectMapper;
    private Clock fixedClock;
    private PostJournalService postJournalService;

    private static final Instant FIXED_NOW = Instant.parse("2026-11-13T15:00:00Z");

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        List<JournalPostingStrategy> strategies = List.of(
                new DepositPostingStrategy(cashShardResolver),
                new WithdrawalPostingStrategy(cashShardResolver),
                new TransferPostingStrategy()
        );
        postJournalService = new PostJournalService(
                ledgerAccountRepositoryPort,
                journalRepositoryPort,
                outboxPort,
                strategies,
                objectMapper,
                fixedClock
        );
    }

    private LedgerAccount buildCustomerAccount(UUID id, long balance, long sequence) {
        return LedgerAccount.builder()
                .id(id)
                .code("ACC-" + id.toString().substring(0, 8))
                .kind(AccountKind.CUSTOMER)
                .normalBalance(NormalBalance.CREDIT)
                .currency("BRL")
                .balance(balance)
                .lastSequence(sequence)
                .status(AccountStatus.ACTIVE)
                .createdAt(FIXED_NOW)
                .updatedAt(FIXED_NOW)
                .build();
    }

    private LedgerAccount buildCashAccount(UUID id, long balance, long sequence) {
        return LedgerAccount.builder()
                .id(id)
                .code("SYS-CASH-01")
                .kind(AccountKind.SYSTEM)
                .normalBalance(NormalBalance.DEBIT)
                .currency("BRL")
                .balance(balance)
                .lastSequence(sequence)
                .status(AccountStatus.ACTIVE)
                .createdAt(FIXED_NOW)
                .updatedAt(FIXED_NOW)
                .build();
    }

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Should return existing journal immediately if transaction was already processed")
        void shouldReturnExistingJournalIfAlreadyProcessed() {
            UUID transactionId = UUID.randomUUID();
            Journal existingJournal = Journal.createPosted(
                    UUID.randomUUID(),
                    transactionId,
                    "DEPOSIT",
                    "Previously posted",
                    10_000L,
                    "BRL",
                    UUID.randomUUID(),
                    FIXED_NOW,
                    List.of(
                            new Entry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EntryDirection.DEBIT, 10_000L, 10_000L, 1L, FIXED_NOW),
                            new Entry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EntryDirection.CREDIT, 10_000L, 10_000L, 1L, FIXED_NOW)
                    )
            );

            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.of(existingJournal));

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "DEPOSIT",
                    10_000L,
                    "BRL",
                    null,
                    UUID.randomUUID(),
                    "TERM-01",
                    UUID.randomUUID(),
                    FIXED_NOW
            );

            Journal result = postJournalService.postJournal(command);

            assertThat(result).isSameAs(existingJournal);
            verify(ledgerAccountRepositoryPort, never()).findByIdForUpdate(any());
            verify(outboxPort, never()).save(any());
            verify(outboxPort, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("Deposit Tests")
    class DepositTests {

        @Test
        @DisplayName("Should execute deposit, lock in UUID order (cash < customer), debit cash and credit customer")
        void shouldDepositWithCashIdSmallerThanCustomerId() throws Exception {
            UUID transactionId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            // Ensure cashShardId < customerAccountId
            UUID cashShardId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID customerAccountId = UUID.fromString("00000000-0000-0000-0000-000000000002");

            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.empty());
            when(cashShardResolver.resolveShardId("TERM-01", transactionId)).thenReturn(cashShardId);

            LedgerAccount cashAccount = buildCashAccount(cashShardId, 100_000L, 5L);
            LedgerAccount customerAccount = buildCustomerAccount(customerAccountId, 10_000L, 2L);

            when(ledgerAccountRepositoryPort.findByIdForUpdate(cashShardId)).thenReturn(Optional.of(cashAccount));
            when(ledgerAccountRepositoryPort.findByIdForUpdate(customerAccountId)).thenReturn(Optional.of(customerAccount));
            when(journalRepositoryPort.save(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "DEPOSIT",
                    5_000L,
                    "BRL",
                    null,
                    customerAccountId,
                    "TERM-01",
                    correlationId,
                    FIXED_NOW
            );

            Journal result = postJournalService.postJournal(command);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(JournalStatus.POSTED);
            assertThat(result.getTotalAmount()).isEqualTo(5_000L);
            assertThat(result.getEntries()).hasSize(2);

            // Assert balances updated correctly
            assertThat(cashAccount.getBalance()).isEqualTo(105_000L); // DEBIT normal balance + 5_000
            assertThat(customerAccount.getBalance()).isEqualTo(15_000L); // CREDIT normal balance + 5_000

            // Assert lock ordering: cash (smaller) then customer (larger)
            InOrder inOrder = inOrder(ledgerAccountRepositoryPort);
            inOrder.verify(ledgerAccountRepositoryPort).findByIdForUpdate(cashShardId);
            inOrder.verify(ledgerAccountRepositoryPort).findByIdForUpdate(customerAccountId);

            // Assert accounts saved
            verify(ledgerAccountRepositoryPort).save(cashAccount);
            verify(ledgerAccountRepositoryPort).save(customerAccount);
            verify(journalRepositoryPort).save(any(Journal.class));

            // Assert Outbox events
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<OutboxEvent>> outboxCaptor = ArgumentCaptor.forClass(List.class);
            verify(outboxPort).saveAll(outboxCaptor.capture());

            List<OutboxEvent> events = outboxCaptor.getValue();
            assertThat(events).hasSize(3);

            OutboxEvent journalEvent = events.get(0);
            assertThat(journalEvent.getTopic()).isEqualTo(JournalPostedEvent.TOPIC);
            assertThat(journalEvent.getEventType()).isEqualTo(JournalPostedEvent.EVENT_TYPE);
            assertThat(journalEvent.getMessageKey()).isEqualTo(transactionId.toString());

            JournalPostedEvent parsedJournal = objectMapper.readValue(journalEvent.getPayload(), JournalPostedEvent.class);
            assertThat(parsedJournal.transactionId()).isEqualTo(transactionId);
            assertThat(parsedJournal.status()).isEqualTo(JournalStatus.POSTED);
            assertThat(parsedJournal.totalAmount()).isEqualTo(5_000L);

            OutboxEvent cashEntryEvent = events.get(1);
            assertThat(cashEntryEvent.getTopic()).isEqualTo(EntryPostedEvent.TOPIC);
            EntryPostedEvent parsedCashEntry = objectMapper.readValue(cashEntryEvent.getPayload(), EntryPostedEvent.class);
            assertThat(parsedCashEntry.accountId()).isEqualTo(cashShardId);
            assertThat(parsedCashEntry.direction()).isEqualTo(EntryDirection.DEBIT);
            assertThat(parsedCashEntry.amount()).isEqualTo(5_000L);
            assertThat(parsedCashEntry.balanceAfter()).isEqualTo(105_000L);

            OutboxEvent customerEntryEvent = events.get(2);
            assertThat(customerEntryEvent.getTopic()).isEqualTo(EntryPostedEvent.TOPIC);
            EntryPostedEvent parsedCustomerEntry = objectMapper.readValue(customerEntryEvent.getPayload(), EntryPostedEvent.class);
            assertThat(parsedCustomerEntry.accountId()).isEqualTo(customerAccountId);
            assertThat(parsedCustomerEntry.direction()).isEqualTo(EntryDirection.CREDIT);
            assertThat(parsedCustomerEntry.amount()).isEqualTo(5_000L);
            assertThat(parsedCustomerEntry.balanceAfter()).isEqualTo(15_000L);
        }

        @Test
        @DisplayName("Should enforce anti-deadlock lock ordering when customerId is smaller than cashShardId")
        void shouldLockInCorrectOrderWhenCustomerIdSmaller() {
            UUID transactionId = UUID.randomUUID();
            UUID customerAccountId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID cashShardId = UUID.fromString("00000000-0000-0000-0000-000000000002");

            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.empty());
            when(cashShardResolver.resolveShardId("TERM-02", transactionId)).thenReturn(cashShardId);

            LedgerAccount customerAccount = buildCustomerAccount(customerAccountId, 1_000L, 1L);
            LedgerAccount cashAccount = buildCashAccount(cashShardId, 50_000L, 10L);

            when(ledgerAccountRepositoryPort.findByIdForUpdate(customerAccountId)).thenReturn(Optional.of(customerAccount));
            when(ledgerAccountRepositoryPort.findByIdForUpdate(cashShardId)).thenReturn(Optional.of(cashAccount));
            when(journalRepositoryPort.save(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "DEPOSIT",
                    2_000L,
                    "BRL",
                    null,
                    customerAccountId,
                    "TERM-02",
                    UUID.randomUUID(),
                    FIXED_NOW
            );

            Journal result = postJournalService.postJournal(command);

            assertThat(result.getStatus()).isEqualTo(JournalStatus.POSTED);

            // Verify customer (smaller) was locked first, then cash (larger)
            InOrder inOrder = inOrder(ledgerAccountRepositoryPort);
            inOrder.verify(ledgerAccountRepositoryPort).findByIdForUpdate(customerAccountId);
            inOrder.verify(ledgerAccountRepositoryPort).findByIdForUpdate(cashShardId);
        }
    }

    @Nested
    @DisplayName("Withdrawal Tests")
    class WithdrawalTests {

        @Test
        @DisplayName("Should execute withdrawal with sufficient balance, debiting customer and crediting cash")
        void shouldWithdrawWithSufficientFunds() throws Exception {
            UUID transactionId = UUID.randomUUID();
            UUID customerAccountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            UUID cashShardId = UUID.fromString("33333333-3333-3333-3333-333333333333");

            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.empty());
            when(cashShardResolver.resolveShardId("TERM-01", transactionId)).thenReturn(cashShardId);

            LedgerAccount customerAccount = buildCustomerAccount(customerAccountId, 20_000L, 3L);
            LedgerAccount cashAccount = buildCashAccount(cashShardId, 100_000L, 10L);

            when(ledgerAccountRepositoryPort.findByIdForUpdate(customerAccountId)).thenReturn(Optional.of(customerAccount));
            when(ledgerAccountRepositoryPort.findByIdForUpdate(cashShardId)).thenReturn(Optional.of(cashAccount));
            when(journalRepositoryPort.save(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "WITHDRAWAL",
                    8_000L,
                    "BRL",
                    customerAccountId,
                    null,
                    "TERM-01",
                    UUID.randomUUID(),
                    FIXED_NOW
            );

            Journal result = postJournalService.postJournal(command);

            assertThat(result.getStatus()).isEqualTo(JournalStatus.POSTED);
            assertThat(result.getTotalAmount()).isEqualTo(8_000L);
            assertThat(customerAccount.getBalance()).isEqualTo(12_000L); // 20_000 - 8_000
            assertThat(cashAccount.getBalance()).isEqualTo(92_000L); // 100_000 - 8_000

            verify(ledgerAccountRepositoryPort).save(customerAccount);
            verify(ledgerAccountRepositoryPort).save(cashAccount);
            verify(outboxPort).saveAll(any());
        }

        @Test
        @DisplayName("Should reject withdrawal when customer balance is insufficient without modifying balances")
        void shouldRejectWithdrawalWhenBalanceInsufficient() throws Exception {
            UUID transactionId = UUID.randomUUID();
            UUID customerAccountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            UUID cashShardId = UUID.fromString("33333333-3333-3333-3333-333333333333");

            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.empty());
            when(cashShardResolver.resolveShardId("TERM-01", transactionId)).thenReturn(cashShardId);

            LedgerAccount customerAccount = buildCustomerAccount(customerAccountId, 5_000L, 3L);
            LedgerAccount cashAccount = buildCashAccount(cashShardId, 100_000L, 10L);

            when(ledgerAccountRepositoryPort.findByIdForUpdate(customerAccountId)).thenReturn(Optional.of(customerAccount));
            when(ledgerAccountRepositoryPort.findByIdForUpdate(cashShardId)).thenReturn(Optional.of(cashAccount));
            when(journalRepositoryPort.save(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "WITHDRAWAL",
                    10_000L, // Requested 10_000 with only 5_000 balance
                    "BRL",
                    customerAccountId,
                    null,
                    "TERM-01",
                    UUID.randomUUID(),
                    FIXED_NOW
            );

            Journal result = postJournalService.postJournal(command);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(JournalStatus.REJECTED);
            assertThat(result.getRejectCode()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(result.getRejectReason()).isEqualTo("Saldo insuficiente para efetuar o saque.");
            assertThat(result.getEntries()).isEmpty();

            // Assert balances were NOT modified
            assertThat(customerAccount.getBalance()).isEqualTo(5_000L);
            assertThat(customerAccount.getLastSequence()).isEqualTo(3L);
            assertThat(cashAccount.getBalance()).isEqualTo(100_000L);

            // Assert accounts were NOT saved
            verify(ledgerAccountRepositoryPort, never()).save(any());

            // Assert rejected journal was saved
            verify(journalRepositoryPort).save(any(Journal.class));

            // Assert JournalRejectedEvent was published to Outbox
            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxPort).save(outboxCaptor.capture());

            OutboxEvent outboxEvent = outboxCaptor.getValue();
            assertThat(outboxEvent.getTopic()).isEqualTo(JournalRejectedEvent.TOPIC);
            assertThat(outboxEvent.getEventType()).isEqualTo(JournalRejectedEvent.EVENT_TYPE);
            assertThat(outboxEvent.getMessageKey()).isEqualTo(transactionId.toString());

            JournalRejectedEvent parsedEvent = objectMapper.readValue(outboxEvent.getPayload(), JournalRejectedEvent.class);
            assertThat(parsedEvent.transactionId()).isEqualTo(transactionId);
            assertThat(parsedEvent.status()).isEqualTo(JournalStatus.REJECTED);
            assertThat(parsedEvent.rejectCode()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(parsedEvent.rejectReason()).isEqualTo("Saldo insuficiente para efetuar o saque.");
        }
    }

    @Nested
    @DisplayName("Transfer and Error Handling Tests")
    class TransferAndErrorTests {

        @Test
        @DisplayName("Should execute customer-to-customer transfer with ordered locks")
        void shouldExecuteTransferSuccessfully() {
            UUID transactionId = UUID.randomUUID();
            UUID sourceId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID destId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.empty());

            LedgerAccount sourceAcc = buildCustomerAccount(sourceId, 50_000L, 1L);
            LedgerAccount destAcc = buildCustomerAccount(destId, 20_000L, 5L);

            when(ledgerAccountRepositoryPort.findByIdForUpdate(sourceId)).thenReturn(Optional.of(sourceAcc));
            when(ledgerAccountRepositoryPort.findByIdForUpdate(destId)).thenReturn(Optional.of(destAcc));
            when(journalRepositoryPort.save(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "TRANSFER",
                    15_000L,
                    "BRL",
                    sourceId,
                    destId,
                    null,
                    UUID.randomUUID(),
                    FIXED_NOW
            );

            Journal result = postJournalService.postJournal(command);

            assertThat(result.getStatus()).isEqualTo(JournalStatus.POSTED);
            assertThat(sourceAcc.getBalance()).isEqualTo(35_000L);
            assertThat(destAcc.getBalance()).isEqualTo(35_000L);

            InOrder inOrder = inOrder(ledgerAccountRepositoryPort);
            inOrder.verify(ledgerAccountRepositoryPort).findByIdForUpdate(sourceId);
            inOrder.verify(ledgerAccountRepositoryPort).findByIdForUpdate(destId);
        }

        @Test
        @DisplayName("Should throw LedgerAccountNotFoundException when account is missing")
        void shouldThrowWhenAccountNotFound() {
            UUID transactionId = UUID.randomUUID();
            UUID cashShardId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID customerAccountId = UUID.fromString("22222222-2222-2222-2222-222222222222");

            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.empty());
            when(cashShardResolver.resolveShardId("TERM-01", transactionId)).thenReturn(cashShardId);
            when(ledgerAccountRepositoryPort.findByIdForUpdate(cashShardId)).thenReturn(Optional.empty());

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "DEPOSIT",
                    5_000L,
                    "BRL",
                    null,
                    customerAccountId,
                    "TERM-01",
                    UUID.randomUUID(),
                    FIXED_NOW
            );

            assertThatThrownBy(() -> postJournalService.postJournal(command))
                    .isInstanceOf(LedgerAccountNotFoundException.class)
                    .hasMessageContaining(cashShardId.toString());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when source and destination accounts are identical")
        void shouldThrowWhenSourceAndDestinationAreIdentical() {
            UUID transactionId = UUID.randomUUID();
            UUID sameAccountId = UUID.randomUUID();

            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.empty());

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "TRANSFER",
                    5_000L,
                    "BRL",
                    sameAccountId,
                    sameAccountId,
                    null,
                    UUID.randomUUID(),
                    FIXED_NOW
            );

            assertThatThrownBy(() -> postJournalService.postJournal(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be distinct");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for unsupported transaction type")
        void shouldThrowForUnsupportedType() {
            UUID transactionId = UUID.randomUUID();
            when(journalRepositoryPort.findByTransactionId(transactionId)).thenReturn(Optional.empty());

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "UNKNOWN_TYPE",
                    5_000L,
                    "BRL",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    FIXED_NOW
            );

            assertThatThrownBy(() -> postJournalService.postJournal(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported transaction type");
        }
    }
}
