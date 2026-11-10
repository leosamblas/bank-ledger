package com.bankledger.account.adapters.out.persistence;

import com.bankledger.account.adapters.out.persistence.entity.AccountJpaEntity;
import com.bankledger.account.adapters.out.persistence.repository.AccountSpringDataRepository;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.domain.model.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter implementing AccountRepositoryPort using Spring Data JPA.
 */
@Repository
@RequiredArgsConstructor
public class AccountPersistenceAdapter implements AccountRepositoryPort {

    private final AccountSpringDataRepository accountRepository;

    @Override
    public Account save(Account account) {
        Objects.requireNonNull(account, "account must not be null");
        AccountJpaEntity entity = accountRepository.findById(account.id())
                .map(existing -> {
                    AccountJpaEntity updated = AccountJpaEntity.fromDomain(account);
                    updated.setVersion(existing.getVersion());
                    return updated;
                })
                .orElseGet(() -> AccountJpaEntity.fromDomain(account));
        AccountJpaEntity saved = accountRepository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return accountRepository.findById(id).map(AccountJpaEntity::toDomain);
    }

    @Override
    public Optional<Account> findByBranchAndNumber(String branch, String number) {
        if (branch == null || number == null) {
            return Optional.empty();
        }
        return accountRepository.findByBranchAndAccountNumber(branch, number)
                .map(AccountJpaEntity::toDomain);
    }

    @Override
    public Optional<Account> findByCustomerId(UUID customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        return accountRepository.findByCustomerId(customerId)
                .map(AccountJpaEntity::toDomain);
    }

    @Override
    public long nextAccountNumber() {
        return accountRepository.nextAccountNumberSequence();
    }
}
