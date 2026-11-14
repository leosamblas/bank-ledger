package com.bankledger.transaction.application.port.in;

import com.bankledger.transaction.domain.model.FinancialTransaction;

/**
 * Inbound port to create an atomic transfer transaction.
 */
public interface CreateTransferUseCase {
    FinancialTransaction createTransfer(CreateTransferCommand command);
}
