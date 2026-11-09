package com.bankledger.commons.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Transactional helper for executing idempotent event consumer actions.
 * Ensures an exactly-once side-effect guarantee using table {@code inbox_event}.
 */
@Component
public class InboxHandler {

    private static final Logger log = LoggerFactory.getLogger(InboxHandler.class);

    private final InboxEventRepository repository;

    public InboxHandler(InboxEventRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * Executes business logic idempotently for the given event ID and handler name.
     * <p>
     * If the event has already been recorded for this handler, execution is skipped.
     * Otherwise, the business logic is executed and an {@link InboxEvent} record is persisted
     * within the same database transaction.
     *
     * @param eventId       the unique identifier of the event
     * @param handlerName   the unique name of the consumer handler
     * @param businessLogic the action to execute if not already processed
     */
    @Transactional
    public void execute(UUID eventId, String handlerName, Runnable businessLogic) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(handlerName, "handlerName must not be null");
        Objects.requireNonNull(businessLogic, "businessLogic must not be null");

        if (repository.existsByEventIdAndHandler(eventId, handlerName)) {
            log.info("Event {} has already been processed by handler '{}', skipping duplicate execution.",
                    eventId, handlerName);
            return;
        }

        businessLogic.run();

        try {
            repository.saveAndFlush(new InboxEvent(eventId, handlerName, Instant.now()));
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate execution detected for event {} and handler '{}', skipping duplicate: {}",
                    eventId, handlerName, ex.getMessage());
        }
    }

    /**
     * Executes a consumer action with payload idempotently for the given event ID and handler name.
     *
     * @param eventId     the unique identifier of the event
     * @param handlerName the unique name of the consumer handler
     * @param consumer    the consumer action to execute if not already processed
     * @param payload     the payload to pass to the consumer
     * @param <T>         the payload type
     */
    @Transactional
    public <T> void execute(UUID eventId, String handlerName, Consumer<T> consumer, T payload) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        execute(eventId, handlerName, () -> consumer.accept(payload));
    }
}
