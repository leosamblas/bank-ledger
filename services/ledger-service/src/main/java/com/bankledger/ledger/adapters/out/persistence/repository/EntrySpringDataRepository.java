package com.bankledger.ledger.adapters.out.persistence.repository;

import com.bankledger.ledger.adapters.out.persistence.entity.EntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EntryEntity}.
 */
public interface EntrySpringDataRepository extends JpaRepository<EntryEntity, UUID> {

    List<EntryEntity> findByJournalId(UUID journalId);

    List<EntryEntity> findByLedgerAccountIdOrderByCreatedAtDesc(UUID ledgerAccountId);
}
