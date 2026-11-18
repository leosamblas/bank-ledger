package com.bankledger.transaction.application.port.out;

import com.bankledger.transaction.domain.model.AccountLimit;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for retrieving and persisting dynamic account limits.
 */
public interface AccountLimitRepositoryPort {

    Optional<AccountLimit> findByAccountId(UUID accountId);

    AccountLimit save(AccountLimit accountLimit);
}
