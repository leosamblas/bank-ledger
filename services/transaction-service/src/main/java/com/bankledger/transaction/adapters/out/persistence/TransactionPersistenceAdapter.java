package com.bankledger.transaction.adapters.out.persistence;

import com.bankledger.transaction.adapters.out.persistence.entity.FinancialTransactionEntity;
import com.bankledger.transaction.adapters.out.persistence.repository.TransactionSpringDataRepository;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import com.bankledger.transaction.domain.model.TransactionStatus;
import com.bankledger.transaction.domain.model.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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
        FinancialTransactionEntity entity = FinancialTransactionEntity.fromDomain(transaction);
        FinancialTransactionEntity saved = repository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<FinancialTransaction> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById(id).map(FinancialTransactionEntity::toDomain);
    }

    @Override
    public long sumDailyTransfers(UUID sourceAccountId, Instant startOfDay) {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(startOfDay, "startOfDay must not be null");
        return repository.sumDailyTransfers(
                sourceAccountId,
                TransactionType.TRANSFER,
                List.of(TransactionStatus.PENDING, TransactionStatus.COMPLETED),
                startOfDay
        );
    }
}
