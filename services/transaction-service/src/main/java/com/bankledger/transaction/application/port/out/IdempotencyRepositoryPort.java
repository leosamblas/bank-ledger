package com.bankledger.transaction.application.port.out;

import com.bankledger.transaction.domain.model.IdempotencyRecord;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepositoryPort {

    Optional<IdempotencyRecord> findByScopeAndKey(UUID scope, UUID key);

    IdempotencyRecord save(IdempotencyRecord record);
}
