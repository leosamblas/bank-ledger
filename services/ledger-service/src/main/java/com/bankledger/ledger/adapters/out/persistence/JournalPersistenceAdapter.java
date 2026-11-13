package com.bankledger.ledger.adapters.out.persistence;

import com.bankledger.ledger.adapters.out.persistence.entity.EntryJpaEntity;
import com.bankledger.ledger.adapters.out.persistence.entity.JournalJpaEntity;
import com.bankledger.ledger.adapters.out.persistence.repository.EntrySpringDataRepository;
import com.bankledger.ledger.adapters.out.persistence.repository.JournalSpringDataRepository;
import com.bankledger.ledger.application.port.out.JournalRepositoryPort;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.Journal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter implementing {@link JournalRepositoryPort} using Spring Data JPA.
 */
@Repository
@RequiredArgsConstructor
public class JournalPersistenceAdapter implements JournalRepositoryPort {

    private final JournalSpringDataRepository journalRepository;
    private final EntrySpringDataRepository entryRepository;

    @Override
    public Journal save(Journal journal) {
        Objects.requireNonNull(journal, "journal must not be null");
        JournalJpaEntity savedJournalEntity = journalRepository.saveAndFlush(JournalJpaEntity.fromDomain(journal));

        if (journal.getEntries() != null && !journal.getEntries().isEmpty()) {
            List<EntryJpaEntity> entryEntities = journal.getEntries().stream()
                    .map(EntryJpaEntity::fromDomain)
                    .toList();
            entryRepository.saveAllAndFlush(entryEntities);
        }

        return savedJournalEntity.toDomain(journal.getEntries());
    }

    @Override
    public Optional<Journal> findByTransactionId(UUID transactionId) {
        if (transactionId == null) {
            return Optional.empty();
        }
        return journalRepository.findByTransactionId(transactionId)
                .map(journalEntity -> {
                    List<EntryJpaEntity> entryEntities = entryRepository.findByJournalId(journalEntity.getId());
                    List<Entry> entries = entryEntities.stream()
                            .map(EntryJpaEntity::toDomain)
                            .toList();
                    return journalEntity.toDomain(entries);
                });
    }
}
