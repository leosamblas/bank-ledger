package com.bankledger.transaction.adapters.out.persistence.repository;

import com.bankledger.transaction.adapters.out.persistence.entity.IdempotencyKeyId;
import com.bankledger.transaction.adapters.out.persistence.entity.IdempotencyKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencySpringDataRepository extends JpaRepository<IdempotencyKeyJpaEntity, IdempotencyKeyId> {

    Optional<IdempotencyKeyJpaEntity> findByScopeAndIdemKey(UUID scope, UUID idemKey);
}
