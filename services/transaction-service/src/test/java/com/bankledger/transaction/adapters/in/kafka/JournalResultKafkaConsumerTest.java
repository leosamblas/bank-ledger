package com.bankledger.transaction.adapters.in.kafka;

import com.bankledger.commons.constant.BankingErrorCodes;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.transaction.application.port.in.HandleJournalResultUseCase;
import com.bankledger.transaction.domain.event.JournalResultPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalResultKafkaConsumer Unit Tests")
class JournalResultKafkaConsumerTest {

    @Mock
    private HandleJournalResultUseCase handleJournalResultUseCase;

    @Mock
    private InboxHandler inboxHandler;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private JournalResultKafkaConsumer consumer;

    private static final String TOPIC = KafkaTopics.LEDGER_JOURNAL_RESULTS_V1;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new JournalResultKafkaConsumer(
                handleJournalResultUseCase,
                inboxHandler,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should parse EventEnvelope format for posted journal, execute within inboxHandler, and acknowledge")
    void shouldProcessEventEnvelopePostedAndAcknowledge() {
        UUID eventId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        String envelopeJson = String.format("""
                {
                  "id": "%s",
                  "source": "ledger-service",
                  "type": "JournalPosted",
                  "datacontenttype": "application/json",
                  "specversion": "1.0",
                  "time": "2026-11-13T20:00:00Z",
                  "data": {
                    "journalId": "%s",
                    "transactionId": "%s",
                    "type": "DEPOSIT",
                    "status": "POSTED",
                    "totalAmount": 50000,
                    "currency": "BRL",
                    "correlationId": "%s",
                    "postedAt": "2026-11-13T20:00:00Z"
                  }
                }
                """, eventId, journalId, transactionId, correlationId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 10L, transactionId.toString(), envelopeJson
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(eq(eventId), eq("JournalResultConsumer"), any(Runnable.class));

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<JournalResultPayload> captor = ArgumentCaptor.forClass(JournalResultPayload.class);
        verify(handleJournalResultUseCase).handleResult(captor.capture());

        JournalResultPayload payload = captor.getValue();
        assertThat(payload.transactionId()).isEqualTo(transactionId);
        assertThat(payload.journalId()).isEqualTo(journalId);
        assertThat(payload.type()).isEqualTo("DEPOSIT");
        assertThat(payload.status()).isEqualTo("POSTED");
        assertThat(payload.totalAmount()).isEqualTo(50000L);
        assertThat(payload.currency()).isEqualTo("BRL");
        assertThat(payload.correlationId()).isEqualTo(correlationId);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should parse direct JSON payload for rejected journal, execute within inboxHandler, and acknowledge")
    void shouldProcessDirectJsonRejectedAndAcknowledge() {
        UUID transactionId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "WITHDRAWAL",
                  "status": "REJECTED",
                  "rejectCode": "INSUFFICIENT_FUNDS",
                  "rejectReason": "Saldo insuficiente para efetuar o saque.",
                  "correlationId": "%s",
                  "rejectedAt": "2026-11-13T20:00:00Z"
                }
                """, transactionId, correlationId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 11L, transactionId.toString(), directJson
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(any(UUID.class), eq("JournalResultConsumer"), any(Runnable.class));

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<JournalResultPayload> captor = ArgumentCaptor.forClass(JournalResultPayload.class);
        verify(handleJournalResultUseCase).handleResult(captor.capture());

        JournalResultPayload payload = captor.getValue();
        assertThat(payload.transactionId()).isEqualTo(transactionId);
        assertThat(payload.status()).isEqualTo("REJECTED");
        assertThat(payload.rejectCode()).isEqualTo(BankingErrorCodes.INSUFFICIENT_FUNDS);
        assertThat(payload.rejectReason()).isEqualTo("Saldo insuficiente para efetuar o saque.");

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should NOT acknowledge and should rethrow exception when inboxHandler or useCase throws")
    void shouldNotAcknowledgeWhenProcessingFails() {
        UUID eventId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        String envelopeJson = String.format("""
                {
                  "id": "%s",
                  "source": "ledger-service",
                  "type": "JournalPosted",
                  "data": {
                    "transactionId": "%s",
                    "status": "POSTED"
                  }
                }
                """, eventId, transactionId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 12L, transactionId.toString(), envelopeJson
        );

        doThrow(new RuntimeException("Database connection failure"))
                .when(inboxHandler).execute(eq(eventId), eq("JournalResultConsumer"), any(Runnable.class));

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection failure");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NOT acknowledge and should rethrow exception when record JSON is malformed")
    void shouldNotAcknowledgeWhenJsonIsMalformed() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 13L, "key-1", "{ invalid json"
        );

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verify(acknowledgment, never()).acknowledge();
        verify(inboxHandler, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("Should NOT acknowledge and should rethrow exception when record value is empty")
    void shouldNotAcknowledgeWhenRecordValueIsEmpty() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 14L, "key-1", ""
        );

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verify(acknowledgment, never()).acknowledge();
    }
}
