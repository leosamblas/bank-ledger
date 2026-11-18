package com.bankledger.query.adapters.out.persistence;

import com.bankledger.query.domain.model.AccountBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_balance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceEntity {

    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @Column(name = "daily_transfer_limit_cents", nullable = false)
    private long dailyTransferLimitCents = 5_000_000L;

    public static AccountBalanceEntity fromDomain(AccountBalance model) {
        return AccountBalanceEntity.builder()
                .accountId(model.accountId())
                .balance(model.balance())
                .currency(model.currency())
                .lastSequence(model.lastSequence())
                .updatedAt(model.updatedAt())
                .dailyTransferLimitCents(model.dailyTransferLimitCents())
                .build();
    }

    public AccountBalance toDomain() {
        return new AccountBalance(
                accountId,
                balance,
                currency,
                lastSequence,
                updatedAt,
                dailyTransferLimitCents
        );
    }
}
