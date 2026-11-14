package com.bankledger.transaction.adapters.out.persistence.repository;

import com.bankledger.transaction.adapters.out.persistence.entity.FinancialTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionSpringDataRepository extends JpaRepository<FinancialTransactionEntity, UUID> {
}
