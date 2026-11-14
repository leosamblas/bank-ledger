package com.bankledger.ledger.adapters.out.persistence.entity;

import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping table {@code entry}.
 */
@Entity
@Table(name = "entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class EntryEntity {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "journal_id", nullable = false)
    private UUID journalId;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 6)
    private EntryDirection direction;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "sequence", nullable = false)
    private Long sequence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Entry toDomain() {
        return new Entry(
                this.id,
                this.journalId,
                this.ledgerAccountId,
                this.direction,
                this.amount != null ? this.amount : 0L,
                this.balanceAfter != null ? this.balanceAfter : 0L,
                this.sequence != null ? this.sequence : 0L,
                this.createdAt
        );
    }

    public static EntryEntity fromDomain(Entry domain) {
        if (domain == null) {
            return null;
        }
        return EntryEntity.builder()
                .id(domain.id())
                .journalId(domain.journalId())
                .ledgerAccountId(domain.ledgerAccountId())
                .direction(domain.direction())
                .amount(domain.amount())
                .balanceAfter(domain.balanceAfter())
                .sequence(domain.sequence())
                .createdAt(domain.createdAt())
                .build();
    }
}
