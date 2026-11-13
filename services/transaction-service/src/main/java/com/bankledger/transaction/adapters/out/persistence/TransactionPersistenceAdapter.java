package com.bankledger.transaction.adapters.out.persistence;

import com.bankledger.transaction.adapters.out.persistence.entity.FinancialTransactionJpaEntity;
import com.bankledger.transaction.adapters.out.persistence.repository.TransactionSpringDataRepository;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {

    private final TransactionSpringDataRepository repository;

    @Override
    public FinancialTransaction save(FinancialTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        FinancialTransactionJpaEntity entity = FinancialTransactionJpaEntity.fromDomain(transaction);
        FinancialTransactionJpaEntity saved = repository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<FinancialTransaction> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById(id).map(FinancialTransactionJpaEntity::toDomain);
    }
}
