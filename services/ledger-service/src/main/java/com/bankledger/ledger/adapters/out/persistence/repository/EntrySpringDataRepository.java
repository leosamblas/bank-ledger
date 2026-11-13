package com.bankledger.ledger.adapters.out.persistence.repository;

import com.bankledger.ledger.adapters.out.persistence.entity.EntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EntryJpaEntity}.
 */
@Repository
public interface EntrySpringDataRepository extends JpaRepository<EntryJpaEntity, UUID> {

    List<EntryJpaEntity> findByJournalId(UUID journalId);

    List<EntryJpaEntity> findByLedgerAccountIdOrderByCreatedAtDesc(UUID ledgerAccountId);
}
