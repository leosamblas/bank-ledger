package com.bankledger.commons.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Service that polls pending {@link OutboxEvent}s in transactional batches
 * and publishes them via an {@link OutboxPublisher} using Java Virtual Threads.
 */
public class OutboxPoller implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    public static final int DEFAULT_BATCH_SIZE = 50;
    public static final long DEFAULT_POLLING_INTERVAL_MS = 200;
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final OutboxEventRepository repository;
    private final OutboxPublisher publisher;
    private final TransactionOperations transactionOperations;
    private final int batchSize;
    private final long pollingIntervalMs;
    private final int maxAttempts;

    private volatile boolean running = false;
    private Thread pollerThread;

    public OutboxPoller(
            OutboxEventRepository repository,
            OutboxPublisher publisher,
            PlatformTransactionManager transactionManager
    ) {
        this(repository, publisher, new TransactionTemplate(transactionManager),
                DEFAULT_BATCH_SIZE, DEFAULT_POLLING_INTERVAL_MS, DEFAULT_MAX_ATTEMPTS);
    }

    public OutboxPoller(
            OutboxEventRepository repository,
            OutboxPublisher publisher,
            PlatformTransactionManager transactionManager,
            int batchSize,
            long pollingIntervalMs,
            int maxAttempts
    ) {
        this(repository, publisher, new TransactionTemplate(transactionManager),
                batchSize, pollingIntervalMs, maxAttempts);
    }

    public OutboxPoller(
            OutboxEventRepository repository,
            OutboxPublisher publisher,
            TransactionOperations transactionOperations
    ) {
        this(repository, publisher, transactionOperations,
                DEFAULT_BATCH_SIZE, DEFAULT_POLLING_INTERVAL_MS, DEFAULT_MAX_ATTEMPTS);
    }

    public OutboxPoller(
            OutboxEventRepository repository,
            OutboxPublisher publisher,
            TransactionOperations transactionOperations,
            int batchSize,
            long pollingIntervalMs,
            int maxAttempts
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.transactionOperations = Objects.requireNonNull(transactionOperations, "transactionOperations must not be null");
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        this.pollingIntervalMs = pollingIntervalMs >= 0 ? pollingIntervalMs : DEFAULT_POLLING_INTERVAL_MS;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
    }

    /**
     * Starts the background polling loop on a dedicated Java Virtual Thread.
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        pollerThread = Thread.ofVirtual()
                .name("outbox-poller-", 0)
                .start(this::runLoop);
        log.info("OutboxPoller started with virtual thread {}", pollerThread.getName());
    }

    /**
     * Stops the background polling loop and interrupts the virtual thread.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (pollerThread != null) {
            pollerThread.interrupt();
            try {
                pollerThread.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("OutboxPoller stopped");
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getPollingIntervalMs() {
        return pollingIntervalMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Thread getPollerThread() {
        return pollerThread;
    }

    private void runLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                pollAndPublish();
                Thread.sleep(Duration.ofMillis(pollingIntervalMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in OutboxPoller execution loop", e);
            }
        }
    }

    /**
     * Executes a transactional polling and publishing cycle:
     * locks up to {@code batchSize} pending records, publishes each, and records results.
     *
     * @return the number of events successfully published in this cycle
     */
    public int pollAndPublish() {
        Integer publishedCount = transactionOperations.execute(status -> {
            List<OutboxEvent> events = repository.lockPendingEvents(batchSize);
            if (events == null || events.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (OutboxEvent event : events) {
                if (event.getAttempts() >= maxAttempts) {
                    log.warn("OutboxEvent {} has reached max attempts ({}), skipping publication",
                            event.getId(), maxAttempts);
                    continue;
                }
                try {
                    publisher.publish(event);
                    event.markPublished(Instant.now());
                    count++;
                } catch (Exception ex) {
                    log.error("Failed to publish OutboxEvent {}, incrementing attempts: {}",
                            event.getId(), ex.getMessage(), ex);
                    event.markFailed();
                }
                repository.save(event);
            }
            return count;
        });

        return publishedCount != null ? publishedCount : 0;
    }
}
