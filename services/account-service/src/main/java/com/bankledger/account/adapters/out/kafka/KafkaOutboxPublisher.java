package com.bankledger.account.adapters.out.kafka;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Outgoing adapter implementing {@link OutboxPublisher} to publish events to Apache Kafka.
 */
@Slf4j
@Component
public class KafkaOutboxPublisher implements OutboxPublisher {

    public static final long DEFAULT_PUBLISH_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
    }

    @Override
    public void publish(OutboxEvent event) throws Exception {
        Objects.requireNonNull(event, "event must not be null");

        log.debug("Publishing OutboxEvent {} to topic '{}' with key '{}'",
                event.getId(), event.getTopic(), event.getMessageKey());

        kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                .get(DEFAULT_PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("Successfully published OutboxEvent {} to topic '{}'",
                event.getId(), event.getTopic());
    }
}
