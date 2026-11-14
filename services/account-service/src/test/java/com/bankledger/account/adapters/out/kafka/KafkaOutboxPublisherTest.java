package com.bankledger.account.adapters.out.kafka;

import com.bankledger.account.config.OutboxPollerProperties;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.outbox.OutboxEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.NetworkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaOutboxPublisher publisher;

    private static final long TIMEOUT_MS = 200L;

    @BeforeEach
    void setUp() {
        OutboxPollerProperties properties = new OutboxPollerProperties();
        properties.setPublishTimeoutMs(TIMEOUT_MS);
        publisher = new KafkaOutboxPublisher(kafkaTemplate, properties);
    }

    @Test
    @DisplayName("Should publish successfully when Kafka responds within configured timeout")
    void shouldPublishSuccessfullyWithinTimeout() throws Exception {
        OutboxEvent event = OutboxEvent.pending(
                "Account",
                UUID.randomUUID(),
                "AccountOpened",
                KafkaTopics.ACCOUNT_EVENTS_V1,
                "acc-123",
                "{\"status\":\"OPEN\"}"
        );

        ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(event.getTopic(), 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(record, metadata);

        when(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publish(event);

        verify(kafkaTemplate).send(event.getTopic(), event.getMessageKey(), event.getPayload());
    }

    @Test
    @DisplayName("Should throw TimeoutException when Kafka does not respond within configured timeout without blocking indefinitely")
    void shouldThrowTimeoutExceptionWhenKafkaExceedsTimeout() {
        OutboxEvent event = OutboxEvent.pending(
                "Account",
                UUID.randomUUID(),
                "AccountOpened",
                KafkaTopics.ACCOUNT_EVENTS_V1,
                "acc-123",
                "{\"status\":\"OPEN\"}"
        );

        CompletableFuture<SendResult<String, String>> neverEndingFuture = new CompletableFuture<>();
        when(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()))
                .thenReturn(neverEndingFuture);

        long startTime = System.currentTimeMillis();
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(TimeoutException.class);
        long elapsed = System.currentTimeMillis() - startTime;

        assertThat(elapsed).isGreaterThanOrEqualTo(TIMEOUT_MS - 50);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5).toMillis());
    }

    @Test
    @DisplayName("Should propagate broker error when sending fails exceptionally")
    void shouldPropagateBrokerErrorWhenSendFails() {
        OutboxEvent event = OutboxEvent.pending(
                "Account",
                UUID.randomUUID(),
                "AccountOpened",
                KafkaTopics.ACCOUNT_EVENTS_V1,
                "acc-123",
                "{\"status\":\"OPEN\"}"
        );

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new NetworkException("Broker connection refused"));

        when(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()))
                .thenReturn(failedFuture);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(NetworkException.class)
                .hasMessageContaining("Broker connection refused");
    }

    @Test
    @DisplayName("Should reject null event")
    void shouldRejectNullEvent() {
        assertThatThrownBy(() -> publisher.publish(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("event must not be null");
    }

    @Test
    @DisplayName("Should reject null kafkaTemplate, null properties, or non-positive timeout in constructor")
    void shouldValidateConstructorArguments() {
        OutboxPollerProperties validProperties = new OutboxPollerProperties();
        validProperties.setPublishTimeoutMs(TIMEOUT_MS);

        assertThatThrownBy(() -> new KafkaOutboxPublisher(null, validProperties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("kafkaTemplate must not be null");

        assertThatThrownBy(() -> new KafkaOutboxPublisher(kafkaTemplate, (OutboxPollerProperties) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties must not be null");

        OutboxPollerProperties invalidZero = new OutboxPollerProperties();
        invalidZero.setPublishTimeoutMs(0);
        assertThatThrownBy(() -> new KafkaOutboxPublisher(kafkaTemplate, invalidZero))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishTimeoutMs must be positive");

        OutboxPollerProperties invalidNegative = new OutboxPollerProperties();
        invalidNegative.setPublishTimeoutMs(-100);
        assertThatThrownBy(() -> new KafkaOutboxPublisher(kafkaTemplate, invalidNegative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishTimeoutMs must be positive");
    }

    @Test
    @DisplayName("Should expose configured publish timeout")
    void shouldExposePublishTimeout() {
        assertThat(publisher.getPublishTimeoutMs()).isEqualTo(TIMEOUT_MS);
    }
}
