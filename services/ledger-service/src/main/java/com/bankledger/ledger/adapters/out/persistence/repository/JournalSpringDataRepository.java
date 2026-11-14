package com.bankledger.ledger.adapters.out.persistence.repository;

import com.bankledger.ledger.adapters.out.persistence.entity.JournalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link JournalEntity}.
 */
public interface JournalSpringDataRepository extends JpaRepository<JournalEntity, UUID> {

    Optional<JournalEntity> findByTransactionId(UUID transactionId);
}
