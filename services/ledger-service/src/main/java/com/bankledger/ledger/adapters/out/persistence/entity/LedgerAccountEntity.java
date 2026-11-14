package com.bankledger.ledger.adapters.out.persistence.entity;

import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping table {@code ledger_account}.
 */
@Entity
@Table(name = "ledger_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class LedgerAccountEntity {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private AccountKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 6)
    private NormalBalance normalBalance;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public LedgerAccount toDomain() {
        return new LedgerAccount(
                this.id,
                this.code,
                this.kind,
                this.normalBalance,
                this.currency,
                this.balance != null ? this.balance : 0L,
                this.lastSequence != null ? this.lastSequence : 0L,
                this.status,
                this.createdAt,
                this.updatedAt,
                this.version != null ? this.version : 0L
        );
    }

    public static LedgerAccountEntity fromDomain(LedgerAccount domain) {
        if (domain == null) {
            return null;
        }
        return LedgerAccountEntity.builder()
                .id(domain.getId())
                .code(domain.getCode())
                .kind(domain.getKind())
                .normalBalance(domain.getNormalBalance())
                .currency(domain.getCurrency())
                .balance(domain.getBalance())
                .lastSequence(domain.getLastSequence())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
