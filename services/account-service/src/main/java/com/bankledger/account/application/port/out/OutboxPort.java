package com.bankledger.account.application.port.out;

import com.bankledger.commons.outbox.OutboxEvent;

/**
 * Outbound port for transactional outbox persistence.
 * Implemented by persistence adapter using platform-commons OutboxEventRepository.
 */
public interface OutboxPort {

    void save(OutboxEvent outboxEvent);
}
