package com.bankledger.transaction.adapters.out.persistence;

import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.outbox.OutboxEventRepository;
import com.bankledger.transaction.application.port.out.OutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public void save(OutboxEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        outboxEventRepository.saveAndFlush(event);
    }
}
