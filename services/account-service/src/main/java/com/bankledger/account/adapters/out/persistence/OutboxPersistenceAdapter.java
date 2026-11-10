package com.bankledger.account.adapters.out.persistence;

import com.bankledger.account.application.port.out.OutboxPort;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * Persistence adapter implementing OutboxPort using Spring Data JPA OutboxEventRepository.
 */
@Repository
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public void save(OutboxEvent outboxEvent) {
        Objects.requireNonNull(outboxEvent, "outboxEvent must not be null");
        outboxEventRepository.saveAndFlush(outboxEvent);
    }
}
