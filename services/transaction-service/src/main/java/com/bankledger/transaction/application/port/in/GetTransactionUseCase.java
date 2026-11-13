package com.bankledger.transaction.application.port.in;

import com.bankledger.transaction.domain.model.FinancialTransaction;

import java.util.UUID;

/**
 * Inbound use case for querying a financial transaction by its ID.
 */
public interface GetTransactionUseCase {

    FinancialTransaction getTransaction(UUID transactionId, UUID requestedBy);
}
