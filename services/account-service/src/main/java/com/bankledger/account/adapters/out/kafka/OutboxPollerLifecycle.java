package com.bankledger.account.adapters.out.kafka;

import com.bankledger.commons.outbox.OutboxEventRepository;
import com.bankledger.commons.outbox.OutboxPoller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final boolean autoStartup;
    private volatile boolean running = false;

    @Autowired
    public OutboxPollerLifecycle(
            OutboxEventRepository repository,
            KafkaOutboxPublisher publisher,
            PlatformTransactionManager transactionManager,
            @Value("${bankledger.outbox.poller.batch-size:50}") int batchSize,
            @Value("${bankledger.outbox.poller.interval-ms:200}") long pollingIntervalMs,
            @Value("${bankledger.outbox.poller.max-attempts:5}") int maxAttempts,
            @Value("${bankledger.outbox.poller.auto-startup:true}") boolean autoStartup
    ) {
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(publisher, "publisher must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");

        this.poller = new OutboxPoller(
                repository,
                publisher,
                transactionManager,
                batchSize,
                pollingIntervalMs,
                maxAttempts
        );
        this.autoStartup = autoStartup;
    }

    public OutboxPollerLifecycle(
            OutboxEventRepository repository,
            KafkaOutboxPublisher publisher,
            PlatformTransactionManager transactionManager
    ) {
        this(repository, publisher, transactionManager,
                OutboxPoller.DEFAULT_BATCH_SIZE,
                OutboxPoller.DEFAULT_POLLING_INTERVAL_MS,
                OutboxPoller.DEFAULT_MAX_ATTEMPTS,
                true);
    }

    @Override
    public synchronized void start() {
        if (!running) {
            log.info("Starting OutboxPollerLifecycle...");
            poller.start();
            running = true;
            log.info("OutboxPollerLifecycle started successfully");
        }
    }

    @Override
    public synchronized void stop() {
        if (running) {
            log.info("Stopping OutboxPollerLifecycle...");
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

    /**
     * Explicitly triggers one synchronous polling and publishing cycle.
     *
     * @return number of events published during this cycle
     */
    public int triggerPolling() {
        return poller.pollAndPublish();
    }

    /**
     * Returns the underlying {@link OutboxPoller} instance.
     *
     * @return poller instance
     */
    public OutboxPoller getPoller() {
        return poller;
    }
}
