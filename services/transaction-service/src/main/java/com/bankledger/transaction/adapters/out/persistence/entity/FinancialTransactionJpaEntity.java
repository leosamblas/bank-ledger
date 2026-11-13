package com.bankledger.transaction.adapters.out.persistence.entity;

import com.bankledger.transaction.domain.model.FinancialTransaction;
import com.bankledger.transaction.domain.model.TransactionStatus;
import com.bankledger.transaction.domain.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "financial_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialTransactionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", length = 140)
    private String description;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static FinancialTransactionJpaEntity fromDomain(FinancialTransaction domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        return FinancialTransactionJpaEntity.builder()
                .id(domain.getId())
                .type(domain.getType())
                .status(domain.getStatus())
                .sourceAccountId(domain.getSourceAccountId())
                .destinationAccountId(domain.getDestinationAccountId())
                .amount(domain.getAmount())
                .currency(domain.getCurrency())
                .description(domain.getDescription())
                .requestedBy(domain.getRequestedBy())
                .failureCode(domain.getFailureCode())
                .failureReason(domain.getFailureReason())
                .correlationId(domain.getCorrelationId())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .completedAt(domain.getCompletedAt())
                .version(domain.getVersion())
                .build();
    }

    public FinancialTransaction toDomain() {
        return new FinancialTransaction(
                id,
                type,
                status,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                description,
                requestedBy,
                failureCode,
                failureReason,
                correlationId,
                createdAt,
                updatedAt,
                completedAt,
                version
        );
    }
}
