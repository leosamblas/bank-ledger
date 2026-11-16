package com.bankledger.notification.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import com.bankledger.notification.application.service.EmailNotificationService;
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
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountReadyKafkaConsumer Unit Tests")
class AccountReadyKafkaConsumerTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private AccountReadyKafkaConsumer consumer;

    private static final String TOPIC = KafkaTopics.ACCOUNT_READY_V1;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new AccountReadyKafkaConsumer(emailNotificationService, objectMapper);
    }

    @Test
    @DisplayName("Should parse KafkaEventEnvelope format, dispatch to email service, and acknowledge")
    void shouldParseEnvelopeAndAcknowledge() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        String envelopeJson = String.format("""
                {
                  "id": "%s",
                  "source": "account-service",
                  "type": "AccountReady",
                  "datacontenttype": "application/json",
                  "specversion": "1.0",
                  "time": "2026-11-16T12:00:00Z",
                  "data": {
                    "customerId": "%s",
                    "accountId": "%s",
                    "email": "customer@example.com",
                    "fullName": "Maria Silva",
                    "branch": "0001",
                    "accountNumber": "987654-3",
                    "readyAt": "2026-11-16T12:00:00Z"
                  }
                }
                """, eventId, customerId, accountId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 100L, accountId.toString(), envelopeJson
        );

        when(emailNotificationService.sendAccountReadyEmail(any(AccountReadyPayload.class)))
                .thenReturn(Mono.empty());

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<AccountReadyPayload> captor = ArgumentCaptor.forClass(AccountReadyPayload.class);
        verify(emailNotificationService, times(1)).sendAccountReadyEmail(captor.capture());

        AccountReadyPayload payload = captor.getValue();
        assertThat(payload.customerId()).isEqualTo(customerId);
        assertThat(payload.accountId()).isEqualTo(accountId);
        assertThat(payload.email()).isEqualTo("customer@example.com");
        assertThat(payload.fullName()).isEqualTo("Maria Silva");
        assertThat(payload.branch()).isEqualTo("0001");
        assertThat(payload.accountNumber()).isEqualTo("987654-3");

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should parse direct JSON payload without envelope and acknowledge")
    void shouldParseDirectPayloadAndAcknowledge() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "customerId": "%s",
                  "accountId": "%s",
                  "email": "joao@example.com",
                  "fullName": "João Santos",
                  "branch": "0002",
                  "accountNumber": "112233-4",
                  "readyAt": "2026-11-16T12:30:00Z"
                }
                """, customerId, accountId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 101L, accountId.toString(), directJson
        );

        when(emailNotificationService.sendAccountReadyEmail(any(AccountReadyPayload.class)))
                .thenReturn(Mono.empty());

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<AccountReadyPayload> captor = ArgumentCaptor.forClass(AccountReadyPayload.class);
        verify(emailNotificationService, times(1)).sendAccountReadyEmail(captor.capture());

        AccountReadyPayload payload = captor.getValue();
        assertThat(payload.customerId()).isEqualTo(customerId);
        assertThat(payload.accountId()).isEqualTo(accountId);
        assertThat(payload.email()).isEqualTo("joao@example.com");
        assertThat(payload.fullName()).isEqualTo("João Santos");
        assertThat(payload.branch()).isEqualTo("0002");
        assertThat(payload.accountNumber()).isEqualTo("112233-4");

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when emailNotificationService fails")
    void shouldNeverAcknowledgeAndRethrowWhenServiceFails() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        String directJson = String.format("""
                {
                  "customerId": "%s",
                  "accountId": "%s",
                  "email": "fail@example.com",
                  "fullName": "Fail Test",
                  "branch": "0001",
                  "accountNumber": "000001-0",
                  "readyAt": "2026-11-16T13:00:00Z"
                }
                """, customerId, accountId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 102L, accountId.toString(), directJson
        );

        when(emailNotificationService.sendAccountReadyEmail(any(AccountReadyPayload.class)))
                .thenReturn(Mono.error(new RuntimeException("SMTP delivery failed")));

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("SMTP delivery failed");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when record value is empty or blank")
    void shouldNeverAcknowledgeWhenRecordValueIsEmpty() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 103L, "key-empty", "    "
        );

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka record value must not be empty");

        verify(emailNotificationService, never()).sendAccountReadyEmail(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when JSON is malformed")
    void shouldNeverAcknowledgeWhenJsonIsMalformed() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 104L, "key-bad", "{ not valid json "
        );

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verify(emailNotificationService, never()).sendAccountReadyEmail(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should NEVER acknowledge and rethrow when required fields are missing")
    void shouldNeverAcknowledgeWhenRequiredFieldsAreMissing() {
        String incompleteJson = """
                {
                  "email": "incomplete@example.com"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 105L, "key-incomplete", incompleteJson
        );

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(Exception.class);

        verify(emailNotificationService, never()).sendAccountReadyEmail(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should throw NullPointerException when record or acknowledgment is null")
    void shouldThrowWhenRecordOrAcknowledgmentIsNull() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 106L, "key", "{}"
        );

        assertThatThrownBy(() -> consumer.onMessage(null, acknowledgment))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("record must not be null");

        assertThatThrownBy(() -> consumer.onMessage(record, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("acknowledgment must not be null");
    }
}
