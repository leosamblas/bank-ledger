package com.bankledger.account.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the transactional outbox poller.
 * <p>
 * Bound to prefix {@code bankledger.outbox.poller} and customizable via
 * environment variables (e.g. {@code OUTBOX_POLLER_BATCH_SIZE}, {@code OUTBOX_POLLER_INTERVAL_MS}).
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "bankledger.outbox.poller")
public class OutboxPollerProperties {

    /**
     * Whether the outbox poller bean and feature is enabled.
     */
    private boolean enabled = true;

    /**
     * Whether the outbox poller thread starts automatically on Spring context refresh.
     */
    private boolean autoStartup = true;

    /**
     * Maximum number of pending outbox events to fetch and lock per polling cycle.
     */
    @Min(1)
    private int batchSize = 50;

    /**
     * Sleep interval in milliseconds between consecutive polling cycles.
     */
    @Positive
    private long intervalMs = 200L;

    /**
     * Maximum number of publishing attempts before an event is abandoned.
     */
    @Min(1)
    private int maxAttempts = 5;

    /**
     * Maximum timeout in milliseconds to wait for Kafka broker acknowledgement.
     */
    @Positive
    private long publishTimeoutMs = 2000L;
}
