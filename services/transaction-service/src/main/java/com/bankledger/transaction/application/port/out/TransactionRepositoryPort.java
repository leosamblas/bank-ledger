package com.bankledger.transaction.application.port.out;

import com.bankledger.transaction.domain.model.FinancialTransaction;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for financial transaction persistence operations.
 */
public interface TransactionRepositoryPort {

    FinancialTransaction save(FinancialTransaction transaction);

    Optional<FinancialTransaction> findById(UUID id);
}
