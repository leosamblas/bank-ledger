package com.bankledger.ledger.adapters.out.persistence;

import com.bankledger.ledger.adapters.out.persistence.entity.LedgerAccountJpaEntity;
import com.bankledger.ledger.adapters.out.persistence.repository.LedgerAccountSpringDataRepository;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter implementing {@link LedgerAccountRepositoryPort} using Spring Data JPA.
 */
@Repository
@RequiredArgsConstructor
public class LedgerAccountPersistenceAdapter implements LedgerAccountRepositoryPort {

    private final LedgerAccountSpringDataRepository repository;

    @Override
    public Optional<LedgerAccount> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(LedgerAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<LedgerAccount> findByIdForUpdate(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findByIdForUpdate(id).map(LedgerAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<LedgerAccount> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return repository.findByCode(code).map(LedgerAccountJpaEntity::toDomain);
    }

    @Override
    public LedgerAccount save(LedgerAccount account) {
        Objects.requireNonNull(account, "account must not be null");
        LedgerAccountJpaEntity entity = repository.findById(account.getId())
                .map(existing -> {
                    LedgerAccountJpaEntity updated = LedgerAccountJpaEntity.fromDomain(account);
                    updated.setVersion(existing.getVersion());
                    return updated;
                })
                .orElseGet(() -> LedgerAccountJpaEntity.fromDomain(account));
        LedgerAccountJpaEntity saved = repository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public long sumDebits() {
        return repository.sumBalanceByNormalBalance(NormalBalance.DEBIT);
    }

    @Override
    public long sumCredits() {
        return repository.sumBalanceByNormalBalance(NormalBalance.CREDIT);
    }
}
