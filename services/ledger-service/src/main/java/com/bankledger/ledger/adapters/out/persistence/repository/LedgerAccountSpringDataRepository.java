package com.bankledger.ledger.adapters.out.persistence.repository;

import com.bankledger.ledger.adapters.out.persistence.entity.LedgerAccountEntity;
import com.bankledger.ledger.domain.model.NormalBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link LedgerAccountEntity}.
 */
public interface LedgerAccountSpringDataRepository extends JpaRepository<LedgerAccountEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LedgerAccountEntity a WHERE a.id = :id")
    Optional<LedgerAccountEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<LedgerAccountEntity> findByCode(String code);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM LedgerAccountEntity a WHERE a.normalBalance = :normalBalance")
    long sumBalanceByNormalBalance(@Param("normalBalance") NormalBalance normalBalance);

    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN a.normal_balance = 'DEBIT' THEN a.balance ELSE 0 END), 0) AS debits,
            COALESCE(SUM(CASE WHEN a.normal_balance = 'CREDIT' THEN a.balance ELSE 0 END), 0) AS credits
        FROM ledger_account a
        """, nativeQuery = true)
    TrialBalanceProjection getTrialBalanceAggregate();
}
