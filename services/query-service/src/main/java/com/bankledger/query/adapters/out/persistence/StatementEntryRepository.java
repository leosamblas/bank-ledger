package com.bankledger.query.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StatementEntryRepository extends JpaRepository<StatementEntryEntity, UUID> {

    List<StatementEntryEntity> findByAccountIdOrderBySequenceDesc(UUID accountId);

    List<StatementEntryEntity> findByAccountIdAndPostedAtBetweenOrderBySequenceDesc(
            UUID accountId,
            Instant from,
            Instant to
    );

    boolean existsByAccountIdAndSequence(UUID accountId, long sequence);

    @Query("""
        SELECT COALESCE(SUM(s.amount), 0)
        FROM StatementEntryEntity s
        WHERE s.accountId = :accountId
          AND UPPER(s.direction) = UPPER(:direction)
          AND (
              UPPER(s.category) = UPPER(:category)
              OR (UPPER(:category) = 'TRANSFER' AND UPPER(s.type) LIKE 'TRANSFER%')
          )
          AND s.postedAt >= :startOfDay
    """)
    Long sumAmountByAccountIdAndDirectionAndCategoryAndPostedAtGreaterThanEqual(
            @Param("accountId") UUID accountId,
            @Param("direction") String direction,
            @Param("category") String category,
            @Param("startOfDay") Instant startOfDay
    );
}
