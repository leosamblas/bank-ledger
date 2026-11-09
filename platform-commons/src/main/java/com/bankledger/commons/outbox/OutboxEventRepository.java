package com.bankledger.commons.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent} with PostgreSQL {@code FOR UPDATE SKIP LOCKED} support.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks and retrieves pending outbox events ordered by creation timestamp,
     * skipping rows currently locked by concurrent transactions.
     *
     * @param batchSize maximum number of records to lock and retrieve
     * @return list of locked pending outbox events
     */
    @Query(value = """
        SELECT * FROM outbox_event
        WHERE published_at IS NULL
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> lockPendingEvents(@Param("batchSize") int batchSize);
}
