package com.bankledger.transaction.application.port.out;

import com.bankledger.commons.outbox.OutboxEvent;

/**
 * Outbound port for transactional outbox persistence.
 */
public interface OutboxPort {

    void save(OutboxEvent event);
}
