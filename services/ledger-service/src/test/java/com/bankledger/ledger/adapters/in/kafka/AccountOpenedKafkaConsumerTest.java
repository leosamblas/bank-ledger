package com.bankledger.ledger.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.account.AccountOpenedPayload;
import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.ledger.application.port.in.ProvisionAccountUseCase;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountOpenedKafkaConsumer Unit Tests")
class AccountOpenedKafkaConsumerTest {

    @Mock
    private ProvisionAccountUseCase provisionAccountUseCase;

    @Mock
    private InboxHandler inboxHandler;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private AccountOpenedKafkaConsumer consumer;

    private static final String TOPIC = KafkaTopics.ACCOUNT_EVENTS_V1;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new AccountOpenedKafkaConsumer(
                provisionAccountUseCase,
                inboxHandler,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should parse EventEnvelope format, execute within inboxHandler, and acknowledge")
    void shouldProcessEventEnvelopeFormatAndAcknowledge() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String envelopeJson = String.format("""
                {
                  "id": "%s",
                  "source": "account-service",
                  "type": "AccountOpened",
                  "datacontenttype": "application/json",
                  "specversion": "1.0",
                  "time": "2026-11-13T10:00:00Z",
                  "data": {
                    "accountId": "%s",
                    "customerId": "%s",
                    "customerCpf": "12345678900",
                    "customerName": "Maria Silva",
                    "branch": "0001",
                    "accountNumber": "100000",
                    "checkDigit": "1",
                    "openedAt": "2026-11-13T10:00:00Z"
                  }
                }
                """, eventId, accountId, customerId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 10L, accountId.toString(), envelopeJson
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(eq(eventId), eq("AccountOpenedConsumer"), any(Runnable.class));

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<AccountOpenedPayload> payloadCaptor = ArgumentCaptor.forClass(AccountOpenedPayload.class);
        verify(provisionAccountUseCase).provisionAccount(payloadCaptor.capture());
        AccountOpenedPayload payload = payloadCaptor.getValue();

        assertThat(payload.accountId()).isEqualTo(accountId);
        assertThat(payload.customerId()).isEqualTo(customerId);
        assertThat(payload.customerCpf()).isEqualTo("12345678900");
        assertThat(payload.customerName()).isEqualTo("Maria Silva");
        assertThat(payload.branch()).isEqualTo("0001");
        assertThat(payload.accountNumber()).isEqualTo("100000");
        assertThat(payload.checkDigit()).isEqualTo("1");
        assertThat(payload.openedAt()).isEqualTo(Instant.parse("2026-11-13T10:00:00Z"));

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should parse direct JSON payload format without envelope and acknowledge")
    void shouldProcessDirectPayloadFormatAndAcknowledge() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "accountId": "%s",
                  "customerId": "%s",
                  "customerCpf": "52998224725",
                  "customerName": "Carlos Drummond",
                  "branch": "0001",
                  "accountNumber": "200000",
                  "checkDigit": "2",
                  "openedAt": "2026-11-13T11:00:00Z"
                }
                """, accountId, customerId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 15L, accountId.toString(), directJson
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(eq(accountId), eq("AccountOpenedConsumer"), any(Runnable.class));

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<AccountOpenedPayload> payloadCaptor = ArgumentCaptor.forClass(AccountOpenedPayload.class);
        verify(provisionAccountUseCase).provisionAccount(payloadCaptor.capture());
        AccountOpenedPayload payload = payloadCaptor.getValue();

        assertThat(payload.accountId()).isEqualTo(accountId);
        assertThat(payload.branch()).isEqualTo("0001");
        assertThat(payload.accountNumber()).isEqualTo("200000");
        assertThat(payload.checkDigit()).isEqualTo("2");

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should extract eventId from Kafka header if present")
    void shouldExtractEventIdFromKafkaHeaderWhenPresent() {
        UUID headerEventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "accountId": "%s",
                  "customerId": "%s",
                  "customerCpf": "12345678900",
                  "customerName": "Maria Silva",
                  "branch": "0001",
                  "accountNumber": "300000",
                  "checkDigit": "3",
                  "openedAt": "2026-11-13T10:00:00Z"
                }
                """, accountId, UUID.randomUUID());

        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("ce_id", headerEventId.toString().getBytes(StandardCharsets.UTF_8)));

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 20L, System.currentTimeMillis(), null, 0, 0,
                accountId.toString(), directJson, headers, null
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(eq(headerEventId), eq("AccountOpenedConsumer"), any(Runnable.class));

        consumer.onMessage(record, acknowledgment);

        verify(inboxHandler).execute(eq(headerEventId), eq("AccountOpenedConsumer"), any(Runnable.class));
        verify(provisionAccountUseCase).provisionAccount(any(AccountOpenedPayload.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when inboxHandler throws exception")
    void shouldNeverAcknowledgeAndRethrowWhenInboxHandlerFails() {
        UUID accountId = UUID.randomUUID();
        String directJson = String.format("""
                {
                  "accountId": "%s",
                  "customerId": "%s",
                  "customerCpf": "12345678900",
                  "customerName": "Maria Silva",
                  "branch": "0001",
                  "accountNumber": "100000",
                  "checkDigit": "1",
                  "openedAt": "2026-11-13T10:00:00Z"
                }
                """, accountId, UUID.randomUUID());

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 25L, accountId.toString(), directJson
        );

        doThrow(new RuntimeException("Database connection error"))
                .when(inboxHandler).execute(any(), eq("AccountOpenedConsumer"), any(Runnable.class));

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection error");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when provisionAccountUseCase fails")
    void shouldNeverAcknowledgeAndRethrowWhenUseCaseFails() {
        UUID accountId = UUID.randomUUID();
        String directJson = String.format("""
                {
                  "accountId": "%s",
                  "customerId": "%s",
                  "customerCpf": "12345678900",
                  "customerName": "Maria Silva",
                  "branch": "0001",
                  "accountNumber": "100000",
                  "checkDigit": "1",
                  "openedAt": "2026-11-13T10:00:00Z"
                }
                """, accountId, UUID.randomUUID());

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 30L, accountId.toString(), directJson
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(inboxHandler).execute(any(), eq("AccountOpenedConsumer"), any(Runnable.class));

        doThrow(new IllegalStateException("Failed to save ledger account"))
                .when(provisionAccountUseCase).provisionAccount(any());

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to save ledger account");

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
        verify(provisionAccountUseCase, never()).provisionAccount(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when required fields are missing")
    void shouldNeverAcknowledgeWhenRequiredFieldsAreMissing() {
        // Missing "branch" and "checkDigit"
        String incompleteJson = """
                {
                  "accountId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "accountNumber": "100000"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 40L, "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", incompleteJson
        );

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(Exception.class);

        verify(inboxHandler, never()).execute(any(), any(), any());
        verify(provisionAccountUseCase, never()).provisionAccount(any());
        verify(acknowledgment, never()).acknowledge();
    }
}
