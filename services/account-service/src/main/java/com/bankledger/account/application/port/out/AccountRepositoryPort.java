package com.bankledger.account.application.port.out;

import com.bankledger.account.domain.model.Account;

import java.util.Optional;
import java.util.UUID;

/**
 * Outgoing repository port for persisting and querying Account aggregates.
 * Defined in application layer, implemented by persistence adapter.
 */
public interface AccountRepositoryPort {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    Optional<Account> findByBranchAndNumber(String branch, String number);

    Optional<Account> findByCustomerId(UUID customerId);

    long nextAccountNumber();
}
