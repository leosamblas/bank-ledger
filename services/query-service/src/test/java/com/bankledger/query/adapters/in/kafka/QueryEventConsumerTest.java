package com.bankledger.query.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
import com.bankledger.commons.events.v1.account.AccountOpenedPayload;
import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.query.adapters.out.persistence.AccountBalanceEntity;
import com.bankledger.query.adapters.out.persistence.AccountBalanceRepository;
import com.bankledger.query.adapters.out.persistence.StatementEntryEntity;
import com.bankledger.query.adapters.out.persistence.StatementEntryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryEventConsumer Unit Tests")
class QueryEventConsumerTest {

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private StatementEntryRepository statementEntryRepository;

    @Mock
    private InboxHandler inboxHandler;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private QueryEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new QueryEventConsumer(
                accountBalanceRepository,
                statementEntryRepository,
                inboxHandler,
                objectMapper
        );
    }

    @Test
    @DisplayName("onAccountOpened: Should parse KafkaEventEnvelope format, execute inboxHandler and acknowledge")
    void shouldProcessAccountOpenedFromEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String envelopeJson = String.format("""
                {
                  "id": "%s",
                  "source": "account-service",
                  "type": "com.bankledger.account.opened",
                  "data": {
                    "accountId": "%s",
                    "customerId": "%s",
                    "customerCpf": "123.456.789-00",
                    "customerName": "Maria Silva",
                    "branch": "0001",
                    "accountNumber": "98765432",
                    "checkDigit": "1",
                    "openedAt": "2026-11-16T12:00:00Z"
                  }
                }
                """, eventId, accountId, customerId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.ACCOUNT_EVENTS_V1, 0, 10L, accountId.toString(), envelopeJson
        );

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(inboxHandler).execute(eq(eventId), eq(QueryEventConsumer.HANDLER_ACCOUNT_OPENED), any(Runnable.class));

        consumer.onAccountOpened(record, acknowledgment);

        verify(inboxHandler, times(1)).execute(eq(eventId), eq(QueryEventConsumer.HANDLER_ACCOUNT_OPENED), any(Runnable.class));
        verify(accountBalanceRepository, times(1)).save(any(AccountBalanceEntity.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("onAccountOpened: Should fallback to direct AccountOpenedPayload when envelope.data() is null")
    void shouldProcessAccountOpenedFromDirectPayload() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "accountId": "%s",
                  "customerId": "%s",
                  "customerCpf": "123.456.789-00",
                  "customerName": "Maria Silva",
                  "branch": "0001",
                  "accountNumber": "98765432",
                  "checkDigit": "1",
                  "openedAt": "2026-11-16T12:00:00Z"
                }
                """, accountId, customerId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.ACCOUNT_EVENTS_V1, 0, 10L, accountId.toString(), directJson
        );

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(inboxHandler).execute(eq(accountId), eq(QueryEventConsumer.HANDLER_ACCOUNT_OPENED), any(Runnable.class));

        consumer.onAccountOpened(record, acknowledgment);

        verify(inboxHandler, times(1)).execute(eq(accountId), eq(QueryEventConsumer.HANDLER_ACCOUNT_OPENED), any(Runnable.class));
        verify(accountBalanceRepository, times(1)).save(any(AccountBalanceEntity.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("onAccountOpened: Should fallback to ce_id header for eventId when envelope has no id")
    void shouldFallbackToHeaderForAccountOpenedEventId() {
        UUID headerEventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "accountId": "%s",
                  "customerId": "%s",
                  "customerCpf": "123.456.789-00",
                  "customerName": "Maria Silva",
                  "branch": "0001",
                  "accountNumber": "98765432",
                  "checkDigit": "1",
                  "openedAt": "2026-11-16T12:00:00Z"
                }
                """, accountId, customerId);

        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("ce_id", headerEventId.toString().getBytes(StandardCharsets.UTF_8)));

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.ACCOUNT_EVENTS_V1, 0, 10L, 0L, null, 0, 0,
                accountId.toString(), directJson, headers, null
        );

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(inboxHandler).execute(eq(headerEventId), eq(QueryEventConsumer.HANDLER_ACCOUNT_OPENED), any(Runnable.class));

        consumer.onAccountOpened(record, acknowledgment);

        verify(inboxHandler, times(1)).execute(eq(headerEventId), eq(QueryEventConsumer.HANDLER_ACCOUNT_OPENED), any(Runnable.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("onEntryPosted: Should parse KafkaEventEnvelope format, update balance and statement entry, and acknowledge")
    void shouldProcessEntryPostedFromEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        String envelopeJson = String.format("""
                {
                  "id": "%s",
                  "source": "ledger-service",
                  "type": "com.bankledger.ledger.entry-posted",
                  "data": {
                    "entryId": "%s",
                    "accountId": "%s",
                    "journalId": "%s",
                    "transactionId": "%s",
                    "direction": "CREDIT",
                    "amount": 5000,
                    "sequence": 1,
                    "balanceAfter": 5000,
                    "postedAt": "2026-11-16T12:00:00Z",
                    "category": "DEPOSIT",
                    "type": "DEPOSIT",
                    "description": "PIX Recebido"
                  }
                }
                """, eventId, entryId, accountId, journalId, transactionId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.LEDGER_ENTRIES_V1, 0, 20L, accountId.toString(), envelopeJson
        );

        when(accountBalanceRepository.findById(accountId)).thenReturn(Optional.empty());
        when(statementEntryRepository.existsByAccountIdAndSequence(accountId, 1L)).thenReturn(false);

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(inboxHandler).execute(eq(eventId), eq(QueryEventConsumer.HANDLER_ENTRY_POSTED), any(Runnable.class));

        consumer.onEntryPosted(record, acknowledgment);

        verify(inboxHandler, times(1)).execute(eq(eventId), eq(QueryEventConsumer.HANDLER_ENTRY_POSTED), any(Runnable.class));

        ArgumentCaptor<AccountBalanceEntity> balanceCaptor = ArgumentCaptor.forClass(AccountBalanceEntity.class);
        verify(accountBalanceRepository, times(1)).save(balanceCaptor.capture());
        AccountBalanceEntity savedBalance = balanceCaptor.getValue();
        assertThat(savedBalance.getAccountId()).isEqualTo(accountId);
        assertThat(savedBalance.getBalance()).isEqualTo(5000L);
        assertThat(savedBalance.getLastSequence()).isEqualTo(1L);

        ArgumentCaptor<StatementEntryEntity> statementCaptor = ArgumentCaptor.forClass(StatementEntryEntity.class);
        verify(statementEntryRepository, times(1)).save(statementCaptor.capture());
        StatementEntryEntity savedStatement = statementCaptor.getValue();
        assertThat(savedStatement.getId()).isEqualTo(entryId);
        assertThat(savedStatement.getAccountId()).isEqualTo(accountId);
        assertThat(savedStatement.getAmount()).isEqualTo(5000L);
        assertThat(savedStatement.getBalanceAfter()).isEqualTo(5000L);
        assertThat(savedStatement.getDirection()).isEqualTo("CREDIT");

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("onEntryPosted: Should parse direct EntryPostedInboundDto when envelope.data() is null")
    void shouldProcessEntryPostedFromDirectPayload() {
        UUID entryId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "entryId": "%s",
                  "accountId": "%s",
                  "direction": "DEBIT",
                  "amount": 2000,
                  "sequence": 2
                }
                """, entryId, accountId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.LEDGER_ENTRIES_V1, 0, 21L, accountId.toString(), directJson
        );

        AccountBalanceEntity existingBalance = AccountBalanceEntity.builder()
                .accountId(accountId)
                .balance(5000L)
                .currency("BRL")
                .lastSequence(1L)
                .updatedAt(Instant.now())
                .build();

        when(accountBalanceRepository.findById(accountId)).thenReturn(Optional.of(existingBalance));
        when(statementEntryRepository.existsByAccountIdAndSequence(accountId, 2L)).thenReturn(false);

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(inboxHandler).execute(eq(entryId), eq(QueryEventConsumer.HANDLER_ENTRY_POSTED), any(Runnable.class));

        consumer.onEntryPosted(record, acknowledgment);

        verify(inboxHandler, times(1)).execute(eq(entryId), eq(QueryEventConsumer.HANDLER_ENTRY_POSTED), any(Runnable.class));

        ArgumentCaptor<AccountBalanceEntity> balanceCaptor = ArgumentCaptor.forClass(AccountBalanceEntity.class);
        verify(accountBalanceRepository, times(1)).save(balanceCaptor.capture());
        AccountBalanceEntity savedBalance = balanceCaptor.getValue();
        assertThat(savedBalance.getBalance()).isEqualTo(3000L); // 5000 - 2000 calculated
        assertThat(savedBalance.getLastSequence()).isEqualTo(2L);

        ArgumentCaptor<StatementEntryEntity> statementCaptor = ArgumentCaptor.forClass(StatementEntryEntity.class);
        verify(statementEntryRepository, times(1)).save(statementCaptor.capture());
        StatementEntryEntity savedStatement = statementCaptor.getValue();
        assertThat(savedStatement.getCategory()).isEqualTo("WITHDRAWAL");
        assertThat(savedStatement.getType()).isEqualTo("WITHDRAWAL");
        assertThat(savedStatement.getDirection()).isEqualTo("DEBIT");
        assertThat(savedStatement.getBalanceAfter()).isEqualTo(3000L);

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("processEntryPosted: Should ignore out-of-order sequence when updating balance")
    void shouldIgnoreOutOfOrderSequence() {
        UUID accountId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        AccountBalanceEntity existingBalance = AccountBalanceEntity.builder()
                .accountId(accountId)
                .balance(10000L)
                .currency("BRL")
                .lastSequence(5L)
                .updatedAt(Instant.now())
                .build();

        when(accountBalanceRepository.findById(accountId)).thenReturn(Optional.of(existingBalance));
        when(statementEntryRepository.existsByAccountIdAndSequence(accountId, 3L)).thenReturn(false);

        EntryPostedInboundDto dto = new EntryPostedInboundDto(
                null, entryId, accountId, null, null, null,
                "CREDIT", null, 1000L, 3L, Instant.now(), null, null,
                null, null, null, null, null, null, null, null, null
        );

        consumer.processEntryPosted(dto, entryId);

        // Sequence 3 < lastSequence 5 -> balance should NOT be saved
        verify(accountBalanceRepository, never()).save(any());
        // Statement entry should still be saved
        verify(statementEntryRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("processEntryPosted: Should skip statement entry when duplicate sequence exists")
    void shouldSkipDuplicateStatementEntry() {
        UUID accountId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        AccountBalanceEntity existingBalance = AccountBalanceEntity.builder()
                .accountId(accountId)
                .balance(10000L)
                .currency("BRL")
                .lastSequence(5L)
                .updatedAt(Instant.now())
                .build();

        when(accountBalanceRepository.findById(accountId)).thenReturn(Optional.of(existingBalance));
        when(statementEntryRepository.existsByAccountIdAndSequence(accountId, 6L)).thenReturn(true);

        EntryPostedInboundDto dto = new EntryPostedInboundDto(
                null, entryId, accountId, null, null, null,
                "CREDIT", null, 1000L, 6L, Instant.now(), null, null,
                null, null, null, null, null, null, null, null, null
        );

        consumer.processEntryPosted(dto, entryId);

        // Balance updated
        verify(accountBalanceRepository, times(1)).save(any());
        // Duplicate statement entry skipped
        verify(statementEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when message payload is empty")
    void shouldThrowWhenEmptyPayload() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.ACCOUNT_EVENTS_V1, 0, 1L, "key", ""
        );

        assertThatThrownBy(() -> consumer.onAccountOpened(record, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kafka record value must not be empty");

        verify(acknowledgment, never()).acknowledge();
    }
}
