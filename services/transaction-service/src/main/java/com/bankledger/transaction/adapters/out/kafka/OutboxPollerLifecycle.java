package com.bankledger.transaction.adapters.out.kafka;

import com.bankledger.commons.outbox.OutboxEventRepository;
import com.bankledger.commons.outbox.OutboxPoller;
import com.bankledger.transaction.config.OutboxPollerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Objects;

/**
 * Spring {@link SmartLifecycle} component managing the background {@link OutboxPoller} virtual thread.
 * Starts automatically on Spring context refresh and stops gracefully on application shutdown.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bankledger.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPollerLifecycle implements SmartLifecycle {

    private final OutboxPoller poller;
    private final OutboxPollerProperties properties;
    private final boolean autoStartup;
    private volatile boolean running = false;

    public OutboxPollerLifecycle(
            OutboxEventRepository repository,
            KafkaOutboxPublisher publisher,
            PlatformTransactionManager transactionManager,
            OutboxPollerProperties properties
    ) {
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(publisher, "publisher must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        this.properties = properties;
        this.poller = new OutboxPoller(
                repository,
                publisher,
                transactionManager,
                properties.getBatchSize(),
                properties.getIntervalMs(),
                properties.getMaxAttempts()
        );
        this.autoStartup = properties.isAutoStartup();
    }

    @Override
    public synchronized void start() {
        if (!running) {
            log.info("Starting OutboxPollerLifecycle for transaction-service...");
            poller.start();
            running = true;
            log.info("OutboxPollerLifecycle started successfully");
        }
    }

    @Override
    public synchronized void stop() {
        if (running) {
            log.info("Stopping OutboxPollerLifecycle for transaction-service...");
            poller.stop();
            running = false;
            log.info("OutboxPollerLifecycle stopped successfully");
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running && poller.isRunning();
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    public int triggerPolling() {
        return poller.pollAndPublish();
    }

    public OutboxPoller getPoller() {
        return poller;
    }

    public OutboxPollerProperties getProperties() {
        return properties;
    }
}
