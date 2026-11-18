package com.bankledger.transaction.adapters.out.persistence.entity;

import com.bankledger.transaction.domain.model.AccountLimit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "account_limit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class AccountLimitEntity {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "daily_transfer_limit_cents", nullable = false)
    private long dailyTransferLimitCents;

    @Column(name = "approved_by", nullable = false, length = 80)
    private String approvedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AccountLimitEntity fromDomain(AccountLimit domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        return AccountLimitEntity.builder()
                .accountId(domain.accountId())
                .dailyTransferLimitCents(domain.dailyTransferLimitCents())
                .approvedBy(domain.approvedBy())
                .updatedAt(domain.updatedAt())
                .build();
    }

    public AccountLimit toDomain() {
        return new AccountLimit(
                accountId,
                dailyTransferLimitCents,
                approvedBy,
                updatedAt
        );
    }
}
