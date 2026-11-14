package com.bankledger.transaction.adapters.out.persistence.repository;

import com.bankledger.transaction.adapters.out.persistence.entity.IdempotencyKeyEntity;
import com.bankledger.transaction.adapters.out.persistence.entity.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencySpringDataRepository extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {

    Optional<IdempotencyKeyEntity> findByScopeAndIdemKey(UUID scope, UUID idemKey);
}
