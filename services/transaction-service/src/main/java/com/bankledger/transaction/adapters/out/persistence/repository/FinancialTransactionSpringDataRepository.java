package com.bankledger.transaction.adapters.out.persistence.repository;

import com.bankledger.transaction.adapters.out.persistence.entity.FinancialTransactionEntity;
import com.bankledger.transaction.domain.model.TransactionStatus;
import com.bankledger.transaction.domain.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface FinancialTransactionSpringDataRepository extends JpaRepository<FinancialTransactionEntity, UUID> {

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0L)
        FROM FinancialTransactionEntity t
        WHERE t.sourceAccountId = :accountId
          AND t.type = :type
          AND t.status IN :statuses
          AND t.createdAt >= :startOfDay
    """)
    long sumDailyTransfers(
            @Param("accountId") UUID accountId,
            @Param("type") TransactionType type,
            @Param("statuses") Collection<TransactionStatus> statuses,
            @Param("startOfDay") Instant startOfDay
    );
}
