package com.bankledger.ledger.application.port.in;

/**
 * Inbound port for computing the double-entry trial balance (balancete de verificação).
 */
public interface GetTrialBalanceUseCase {

    TrialBalanceResult getTrialBalance();
}
