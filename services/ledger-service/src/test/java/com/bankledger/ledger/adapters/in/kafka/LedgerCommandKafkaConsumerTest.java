package com.bankledger.ledger.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.ledger.application.port.in.PostJournalUseCase;
import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.model.Journal;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerCommandKafkaConsumer Unit Tests")
class LedgerCommandKafkaConsumerTest {

    @Mock
    private PostJournalUseCase postJournalUseCase;

    @Mock
    private InboxHandler inboxHandler;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private LedgerCommandKafkaConsumer consumer;

    private static final String TOPIC = KafkaTopics.LEDGER_COMMANDS_V1;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new LedgerCommandKafkaConsumer(
                postJournalUseCase,
                inboxHandler,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should parse EventEnvelope format, execute within inboxHandler, and acknowledge")
    void shouldProcessEventEnvelopeFormatAndAcknowledge() {
        UUID eventId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID destAccountId = UUID.randomUUID();

        String envelopeJson = String.format("""
                {
                  "id": "%s",
                  "source": "transaction-service",
                  "type": "com.bankledger.transaction.command.PostJournal",
                  "datacontenttype": "application/json",
                  "specversion": "1.0",
                  "time": "2026-11-13T10:00:00Z",
                  "data": {
                    "transactionId": "%s",
                    "type": "DEPOSIT",
                    "amount": 50000,
                    "currency": "BRL",
                    "destinationAccountId": "%s",
                    "terminalId": "ATM-001"
                  }
                }
                """, eventId, transactionId, destAccountId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 10L, transactionId.toString(), envelopeJson
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(eq(eventId), eq("LedgerCommandHandler"), any(Runnable.class));

        Journal mockJournal = mock(Journal.class);
        doAnswer(invocation -> mockJournal).when(postJournalUseCase).postJournal(any(PostJournalCommand.class));

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<PostJournalCommand> commandCaptor = ArgumentCaptor.forClass(PostJournalCommand.class);
        verify(postJournalUseCase).postJournal(commandCaptor.capture());
        PostJournalCommand captured = commandCaptor.getValue();

        assertThat(captured.transactionId()).isEqualTo(transactionId);
        assertThat(captured.type()).isEqualTo("DEPOSIT");
        assertThat(captured.amount()).isEqualTo(50000L);
        assertThat(captured.currency()).isEqualTo("BRL");
        assertThat(captured.destinationAccountId()).isEqualTo(destAccountId);
        assertThat(captured.terminalId()).isEqualTo("ATM-001");

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should parse direct JSON payload format without envelope and acknowledge")
    void shouldProcessDirectPayloadFormatAndAcknowledge() {
        UUID transactionId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "WITHDRAWAL",
                  "amount": 20000,
                  "currency": "BRL",
                  "sourceAccountId": "%s",
                  "terminalId": "ATM-002"
                }
                """, transactionId, sourceAccountId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 15L, transactionId.toString(), directJson
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(eq(transactionId), eq("LedgerCommandHandler"), any(Runnable.class));

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<PostJournalCommand> commandCaptor = ArgumentCaptor.forClass(PostJournalCommand.class);
        verify(postJournalUseCase).postJournal(commandCaptor.capture());
        PostJournalCommand captured = commandCaptor.getValue();

        assertThat(captured.transactionId()).isEqualTo(transactionId);
        assertThat(captured.type()).isEqualTo("WITHDRAWAL");
        assertThat(captured.amount()).isEqualTo(20000L);
        assertThat(captured.sourceAccountId()).isEqualTo(sourceAccountId);

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should extract eventId from Kafka header if present")
    void shouldExtractEventIdFromKafkaHeaderWhenPresent() {
        UUID headerEventId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID destAccountId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "DEPOSIT",
                  "amount": 10000,
                  "destinationAccountId": "%s"
                }
                """, transactionId, destAccountId);

        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("ce_id", headerEventId.toString().getBytes(StandardCharsets.UTF_8)));

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 20L, System.currentTimeMillis(), null, 0, 0,
                transactionId.toString(), directJson, headers, null
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(eq(headerEventId), eq("LedgerCommandHandler"), any(Runnable.class));

        consumer.onMessage(record, acknowledgment);

        verify(inboxHandler).execute(eq(headerEventId), eq("LedgerCommandHandler"), any(Runnable.class));
        verify(postJournalUseCase).postJournal(any(PostJournalCommand.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when inboxHandler throws exception")
    void shouldNeverAcknowledgeAndRethrowWhenInboxHandlerFails() {
        UUID transactionId = UUID.randomUUID();
        String directJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "DEPOSIT",
                  "amount": 50000,
                  "destinationAccountId": "%s"
                }
                """, transactionId, UUID.randomUUID());

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 25L, transactionId.toString(), directJson
        );

        doThrow(new RuntimeException("Database error during inbox processing"))
                .when(inboxHandler).execute(any(), eq("LedgerCommandHandler"), any(Runnable.class));

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error during inbox processing");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when postJournalUseCase fails")
    void shouldNeverAcknowledgeAndRethrowWhenUseCaseFails() {
        UUID transactionId = UUID.randomUUID();
        String directJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "DEPOSIT",
                  "amount": 50000,
                  "destinationAccountId": "%s"
                }
                """, transactionId, UUID.randomUUID());

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 30L, transactionId.toString(), directJson
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(any(), eq("LedgerCommandHandler"), any(Runnable.class));

        doThrow(new IllegalStateException("Failed to post journal in service"))
                .when(postJournalUseCase).postJournal(any());

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to post journal in service");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when record payload is malformed JSON")
    void shouldNeverAcknowledgeWhenJsonIsMalformed() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 35L, "key-1", "{ broken json content "
        );

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(Exception.class);

        verify(inboxHandler, never()).execute(any(), any(), any());
        verify(postJournalUseCase, never()).postJournal(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when required fields are missing")
    void shouldNeverAcknowledgeWhenRequiredFieldsAreMissing() {
        // Missing "type" and "amount"
        String incompleteJson = String.format("""
                {
                  "transactionId": "%s"
                }
                """, UUID.randomUUID());

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 40L, "key-1", incompleteJson
        );

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(Exception.class);

        verify(inboxHandler, never()).execute(any(), any(), any());
        verify(postJournalUseCase, never()).postJournal(any());
        verify(acknowledgment, never()).acknowledge();
    }
}
