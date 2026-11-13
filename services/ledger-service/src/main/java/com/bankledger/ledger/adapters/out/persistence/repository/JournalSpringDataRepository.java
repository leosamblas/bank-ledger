package com.bankledger.ledger.adapters.out.persistence.repository;

import com.bankledger.ledger.adapters.out.persistence.entity.JournalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link JournalJpaEntity}.
 */
@Repository
public interface JournalSpringDataRepository extends JpaRepository<JournalJpaEntity, UUID> {

    Optional<JournalJpaEntity> findByTransactionId(UUID transactionId);
}
