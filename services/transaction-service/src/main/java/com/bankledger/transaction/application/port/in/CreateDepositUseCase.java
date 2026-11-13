package com.bankledger.transaction.application.port.in;

import com.bankledger.transaction.domain.model.FinancialTransaction;

/**
 * Inbound use case for creating a deposit transaction.
 */
public interface CreateDepositUseCase {

    FinancialTransaction createDeposit(CreateDepositCommand command);
}
