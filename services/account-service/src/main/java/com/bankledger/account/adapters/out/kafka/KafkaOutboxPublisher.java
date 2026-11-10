package com.bankledger.account.adapters.out.kafka;

import com.bankledger.account.config.OutboxPollerProperties;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Outgoing adapter publishing transactional outbox events to Apache Kafka asynchronously
 * with non-blocking timeout handling.
 */
@Slf4j
@Component
public class KafkaOutboxPublisher implements OutboxPublisher {

    public static final long DEFAULT_PUBLISH_TIMEOUT_MS = 2000L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long publishTimeoutMs;

    public KafkaOutboxPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxPollerProperties properties
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        if (properties.getPublishTimeoutMs() <= 0) {
            throw new IllegalArgumentException("publishTimeoutMs must be positive: " + properties.getPublishTimeoutMs());
        }
        this.publishTimeoutMs = properties.getPublishTimeoutMs();
    }

    @Override
    public void publish(OutboxEvent event) throws Exception {
        Objects.requireNonNull(event, "event must not be null");

        log.debug("Publishing OutboxEvent {} to topic '{}' with key '{}' (timeout: {}ms)",
                event.getId(), event.getTopic(), event.getMessageKey(), publishTimeoutMs);

        try {
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(publishTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        log.info("Successfully published OutboxEvent {} to topic '{}'",
                event.getId(), event.getTopic());
    }

    public long getPublishTimeoutMs() {
        return publishTimeoutMs;
    }
}
