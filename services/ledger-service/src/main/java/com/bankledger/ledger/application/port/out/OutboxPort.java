package com.bankledger.ledger.application.port.out;

import com.bankledger.commons.outbox.OutboxEvent;

import java.util.List;

/**
 * Outbound port for transactional outbox persistence.
 */
public interface OutboxPort {

    /**
     * Persists a single outbox event within the ongoing transaction.
     */
    void save(OutboxEvent outboxEvent);

    /**
     * Persists multiple outbox events within the ongoing transaction.
     */
    void saveAll(List<OutboxEvent> events);
}
