package com.bankledger.account.adapters.out.kafka;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Outgoing adapter implementing {@link OutboxPublisher} to publish transactional
 * outbox events to Apache Kafka topics asynchronously.
 * <p>
 * <b>Architectural Decoupling & Latency Guarantee:</b>
 * The REST API (e.g., {@code POST /api/v1/accounts}) NEVER calls Apache Kafka directly.
 * Instead, the application layer persists the domain entity along with its corresponding
 * {@link OutboxEvent} into PostgreSQL within the same local ACID transaction (&lt; 5 ms).
 * A background virtual-thread poller ({@link OutboxPollerLifecycle}) subsequently invokes
 * this adapter asynchronously outside the user HTTP request path.
 * <p>
 * Consequently, Kafka broker latency, rebalances, network partitions, or outages have
 * zero impact on API response times. Publication uses a non-blocking {@code CompletableFuture}
 * bounded by a small, configurable timeout ({@value DEFAULT_PUBLISH_TIMEOUT_MS} ms by default,
 * overridable via {@code OUTBOX_POLLER_PUBLISH_TIMEOUT_MS}) to fail fast and trigger
 * immediate retry/backoff in the poller loop.
 */
@Slf4j
@Component
public class KafkaOutboxPublisher implements OutboxPublisher {

    public static final long DEFAULT_PUBLISH_TIMEOUT_MS = 2000L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long publishTimeoutMs;

    public KafkaOutboxPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${bankledger.outbox.poller.publish-timeout-ms:2000}") long publishTimeoutMs
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        if (publishTimeoutMs <= 0) {
            throw new IllegalArgumentException("publishTimeoutMs must be positive: " + publishTimeoutMs);
        }
        this.publishTimeoutMs = publishTimeoutMs;
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
