package com.bankledger.account.adapters.out.persistence;

import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.domain.model.Account;
import org.springframework.stereotype.Repository;

/**
 * Outbound persistence adapter implementing AccountRepositoryPort.
 */
@Repository
public class AccountRepositoryAdapter implements AccountRepositoryPort {

    @Override
    public void save(Account account) {
    }
}

