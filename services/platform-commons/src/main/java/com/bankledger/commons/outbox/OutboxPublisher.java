package com.bankledger.commons.outbox;

/**
 * Functional interface for publishing an {@link OutboxEvent} to an external messaging broker (e.g., Kafka).
 */
@FunctionalInterface
public interface OutboxPublisher {

    /**
     * Publishes the specified outbox event.
     *
     * @param event the event to publish
     * @throws Exception if an error occurs during publication
     */
    void publish(OutboxEvent event) throws Exception;
}
