package com.bankledger.auth;

import com.bankledger.auth.adapters.out.kafka.KafkaAccountReadyEventPublisher;
import com.bankledger.auth.domain.event.AccountReadyEvent;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaAccountReadyEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private KafkaAccountReadyEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaAccountReadyEventPublisher(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("Should serialize AccountReadyEvent to KafkaEventEnvelope<AccountReadyPayload> and publish to topic account.ready.v1")
    void shouldPublishAccountReadyEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        AccountReadyPayload payload = new AccountReadyPayload(
                customerId,
                accountId,
                "maria@example.com",
                "Maria Souza",
                "0001",
                "987654",
                now
        );
        AccountReadyEvent event = AccountReadyEvent.of(payload, now);

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopics.ACCOUNT_READY_V1);
        assertThat(keyCaptor.getValue()).isEqualTo(customerId.toString());

        String json = payloadCaptor.getValue();
        KafkaEventEnvelope<AccountReadyPayload> envelope = objectMapper.readValue(
                json,
                new TypeReference<KafkaEventEnvelope<AccountReadyPayload>>() {}
        );

        assertThat(envelope.type()).isEqualTo("AccountReady");
        assertThat(envelope.source()).isEqualTo("auth-service");
        assertThat(envelope.data()).isNotNull();
        assertThat(envelope.data().customerId()).isEqualTo(customerId);
        assertThat(envelope.data().accountId()).isEqualTo(accountId);
        assertThat(envelope.data().email()).isEqualTo("maria@example.com");
        assertThat(envelope.data().fullName()).isEqualTo("Maria Souza");
        assertThat(envelope.data().branch()).isEqualTo("0001");
        assertThat(envelope.data().accountNumber()).isEqualTo("987654");
    }

    @Test
    @DisplayName("Should skip publishing gracefully when KafkaTemplate is null")
    void shouldSkipWhenKafkaTemplateIsNull() {
        KafkaAccountReadyEventPublisher nullPublisher = new KafkaAccountReadyEventPublisher(null, objectMapper);
        AccountReadyPayload payload = new AccountReadyPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test@example.com",
                "Test",
                "0001",
                "123",
                Instant.now()
        );
        AccountReadyEvent event = AccountReadyEvent.of(payload, Instant.now());

        // Should not throw
        nullPublisher.publish(event);
    }
}
