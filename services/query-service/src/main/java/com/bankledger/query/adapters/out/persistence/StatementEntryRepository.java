package com.bankledger.query.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface StatementEntryRepository extends JpaRepository<StatementEntryEntity, UUID> {

    List<StatementEntryEntity> findByAccountIdOrderBySequenceDesc(UUID accountId);

    List<StatementEntryEntity> findByAccountIdOrderByPostedAtDescSequenceDesc(UUID accountId);

    List<StatementEntryEntity> findByAccountIdAndPostedAtBetweenOrderBySequenceDesc(
            UUID accountId,
            Instant from,
            Instant to
    );

    boolean existsByAccountIdAndSequence(UUID accountId, long sequence);
}
