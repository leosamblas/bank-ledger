package com.bankledger.account.adapters.out.persistence.entity;

import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.account.domain.vo.AccountNumber;
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

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping for account table.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class AccountEntity {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "branch", nullable = false, length = 4)
    private String branch;

    @Column(name = "account_number", nullable = false, length = 10)
    private String accountNumber;

    @Column(name = "check_digit", nullable = false, length = 1)
    private String checkDigit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public Account toDomain() {
        return new Account(
                id,
                customerId,
                branch,
                AccountNumber.of(branch, accountNumber, checkDigit),
                status,
                createdAt,
                updatedAt,
                closedAt
        );
    }

    public static AccountEntity fromDomain(Account domain) {
        if (domain == null) {
            return null;
        }
        return AccountEntity.builder()
                .id(domain.id())
                .customerId(domain.customerId())
                .branch(domain.branch())
                .accountNumber(domain.accountNumber().number())
                .checkDigit(domain.accountNumber().checkDigit())
                .status(domain.status())
                .createdAt(domain.createdAt())
                .updatedAt(domain.updatedAt())
                .closedAt(domain.closedAt())
                .build();
    }
}
