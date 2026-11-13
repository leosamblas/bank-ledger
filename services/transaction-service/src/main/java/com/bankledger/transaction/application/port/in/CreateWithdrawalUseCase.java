package com.bankledger.transaction.application.port.in;

import com.bankledger.transaction.domain.model.FinancialTransaction;

/**
 * Inbound use case for creating a withdrawal transaction.
 */
public interface CreateWithdrawalUseCase {

    FinancialTransaction createWithdrawal(CreateWithdrawalCommand command);
}
