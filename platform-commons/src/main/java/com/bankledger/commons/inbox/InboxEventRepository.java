package com.bankledger.commons.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for managing {@link InboxEvent} records in table {@code inbox_event}.
 */
@Repository
public interface InboxEventRepository extends JpaRepository<InboxEvent, InboxEventId> {

    /**
     * Checks if an event has already been processed by the specified handler.
     *
     * @param eventId the unique identifier of the event
     * @param handler the handler identifier
     * @return {@code true} if an inbox record exists, {@code false} otherwise
     */
    boolean existsByEventIdAndHandler(UUID eventId, String handler);
}
