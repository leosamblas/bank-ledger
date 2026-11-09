package com.bankledger.account.application.port.out;

import com.bankledger.account.domain.model.Account;

/**
 * Outbound port for account persistence.
 */
public interface AccountRepositoryPort {

    void save(Account account);
}
