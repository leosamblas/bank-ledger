package com.bankledger.transaction.adapters.out.persistence;

import com.bankledger.transaction.adapters.out.persistence.entity.AccountLimitEntity;
import com.bankledger.transaction.adapters.out.persistence.repository.AccountLimitSpringDataRepository;
import com.bankledger.transaction.application.port.out.AccountLimitRepositoryPort;
import com.bankledger.transaction.domain.model.AccountLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountLimitPersistenceAdapter implements AccountLimitRepositoryPort {

    private final AccountLimitSpringDataRepository repository;

    @Override
    public Optional<AccountLimit> findByAccountId(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        return repository.findById(accountId).map(AccountLimitEntity::toDomain);
    }

    @Override
    public AccountLimit save(AccountLimit accountLimit) {
        Objects.requireNonNull(accountLimit, "accountLimit must not be null");
        AccountLimitEntity entity = AccountLimitEntity.fromDomain(accountLimit);
        AccountLimitEntity saved = repository.saveAndFlush(entity);
        return saved.toDomain();
    }
}
