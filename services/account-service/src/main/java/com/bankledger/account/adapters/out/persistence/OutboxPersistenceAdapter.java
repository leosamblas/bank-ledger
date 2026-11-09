package com.bankledger.account.adapters.out.persistence;

import com.bankledger.account.application.port.out.OutboxPort;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.outbox.OutboxEventRepository;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * Persistence adapter implementing OutboxPort using Spring Data JPA OutboxEventRepository.
 */
@Repository
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxPersistenceAdapter(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository, "outboxEventRepository must not be null");
    }

    @Override
    public void save(OutboxEvent outboxEvent) {
        Objects.requireNonNull(outboxEvent, "outboxEvent must not be null");
        outboxEventRepository.saveAndFlush(outboxEvent);
    }
}
