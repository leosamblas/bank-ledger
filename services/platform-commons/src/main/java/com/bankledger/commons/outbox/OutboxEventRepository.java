package com.bankledger.commons.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent} with PostgreSQL {@code FOR UPDATE SKIP LOCKED}
 * and lease-based claim timeout support for distributed workers.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks and retrieves pending outbox events ordered by creation timestamp,
     * skipping rows currently locked or actively claimed by concurrent worker instances.
     *
     * @param batchSize maximum number of records to lock and retrieve
     * @param now current instant to filter out active claims
     * @param maxAttempts maximum attempts threshold
     * @return list of locked pending outbox events
     */
    @Query(value = """
        SELECT * FROM outbox_event
        WHERE published_at IS NULL
          AND (locked_until IS NULL OR locked_until < :now)
          AND attempts < :maxAttempts
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> lockPendingEvents(
            @Param("batchSize") int batchSize,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts
    );

    /**
     * Legacy/convenience overload delegating with current instant and unbounded attempts.
     */
    default List<OutboxEvent> lockPendingEvents(int batchSize) {
        return lockPendingEvents(batchSize, Instant.now(), Integer.MAX_VALUE);
    }
}
