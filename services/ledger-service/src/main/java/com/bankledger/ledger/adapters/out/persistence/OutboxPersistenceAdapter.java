package com.bankledger.ledger.adapters.out.persistence;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.outbox.OutboxEventRepository;
import com.bankledger.ledger.application.port.out.OutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * Persistence adapter implementing {@link OutboxPort} using Spring Data JPA {@link OutboxEventRepository}.
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

    @Override
    public void saveAll(List<OutboxEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        outboxEventRepository.saveAllAndFlush(events);
    }
}
