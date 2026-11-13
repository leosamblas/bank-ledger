package com.bankledger.ledger.adapters.out.persistence.repository;

import com.bankledger.ledger.adapters.out.persistence.entity.LedgerAccountJpaEntity;
import com.bankledger.ledger.domain.model.NormalBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link LedgerAccountJpaEntity}.
 */
@Repository
public interface LedgerAccountSpringDataRepository extends JpaRepository<LedgerAccountJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LedgerAccountJpaEntity a WHERE a.id = :id")
    Optional<LedgerAccountJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<LedgerAccountJpaEntity> findByCode(String code);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM LedgerAccountJpaEntity a WHERE a.normalBalance = :normalBalance")
    long sumBalanceByNormalBalance(@Param("normalBalance") NormalBalance normalBalance);
}
