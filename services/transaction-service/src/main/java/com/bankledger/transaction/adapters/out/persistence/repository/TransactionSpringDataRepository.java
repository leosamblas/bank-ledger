package com.bankledger.transaction.adapters.out.persistence.repository;

import com.bankledger.transaction.adapters.out.persistence.entity.FinancialTransactionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionSpringDataRepository extends JpaRepository<FinancialTransactionJpaEntity, UUID> {
}
