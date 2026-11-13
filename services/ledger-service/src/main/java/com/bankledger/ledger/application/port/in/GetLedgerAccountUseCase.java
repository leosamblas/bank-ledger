package com.bankledger.ledger.application.port.in;

import com.bankledger.ledger.domain.model.LedgerAccount;

import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port for querying ledger account balance and status.
 */
public interface GetLedgerAccountUseCase {

    Optional<LedgerAccount> getAccount(UUID id);
}
