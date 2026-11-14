package com.bankledger.transaction.adapters.out.persistence;

import com.bankledger.transaction.adapters.out.persistence.entity.IdempotencyKeyEntity;
import com.bankledger.transaction.adapters.out.persistence.entity.IdempotencyKeyId;
import com.bankledger.transaction.adapters.out.persistence.repository.IdempotencySpringDataRepository;
import com.bankledger.transaction.application.port.out.IdempotencyRepositoryPort;
import com.bankledger.transaction.domain.exception.IdempotencyConflictException;
import com.bankledger.transaction.domain.model.IdempotencyRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class IdempotencyPersistenceAdapter implements IdempotencyRepositoryPort {

    private final IdempotencySpringDataRepository repository;

    @Override
    public Optional<IdempotencyRecord> findByScopeAndKey(UUID scope, UUID key) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return repository.findById(new IdempotencyKeyId(scope, key))
                .map(IdempotencyKeyEntity::toDomain);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        try {
            IdempotencyKeyEntity entity = IdempotencyKeyEntity.fromDomain(record);
            IdempotencyKeyEntity saved = repository.saveAndFlush(entity);
            return saved.toDomain();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate key detected for idempotency scope={} key={}: {}",
                    record.scope(), record.key(), ex.getMessage());
            throw new IdempotencyConflictException("Operação em processamento com a chave de idempotência informada.", ex);
        }
    }
}
