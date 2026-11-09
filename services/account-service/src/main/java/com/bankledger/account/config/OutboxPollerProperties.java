package com.bankledger.account.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the transactional outbox poller.
 * <p>
 * Bound to prefix {@code bankledger.outbox.poller} and customizable via
 * environment variables (e.g. {@code OUTBOX_POLLER_BATCH_SIZE}, {@code OUTBOX_POLLER_INTERVAL_MS}).
 */
@Getter
@Setter
@Component
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
    private int batchSize = 50;

    /**
     * Sleep interval in milliseconds between consecutive polling cycles.
     */
    private long intervalMs = 200L;

    /**
     * Maximum number of publishing attempts before an event is abandoned.
     */
    private int maxAttempts = 5;

    /**
     * Maximum timeout in milliseconds to wait for Kafka broker acknowledgement.
     */
    private long publishTimeoutMs = 2000L;
}
