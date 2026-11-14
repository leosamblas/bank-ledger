package com.bankledger.ledger.adapters.out.persistence.entity;

import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.JournalStatus;
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
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping table {@code journal}.
 */
@Entity
@Table(name = "journal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class JournalEntity {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private JournalStatus status;

    @Column(name = "reject_code", length = 40)
    private String rejectCode;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "description", length = 140)
    private String description;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    public Journal toDomain(List<Entry> entries) {
        return new Journal(
                this.id,
                this.transactionId,
                this.type,
                this.status,
                this.rejectCode,
                this.rejectReason,
                this.description,
                this.totalAmount != null ? this.totalAmount : 0L,
                this.currency,
                this.correlationId,
                this.postedAt,
                entries != null ? entries : Collections.emptyList()
        );
    }

    public static JournalEntity fromDomain(Journal domain) {
        if (domain == null) {
            return null;
        }
        return JournalEntity.builder()
                .id(domain.getId())
                .transactionId(domain.getTransactionId())
                .type(domain.getType())
                .status(domain.getStatus())
                .rejectCode(domain.getRejectCode())
                .rejectReason(domain.getRejectReason())
                .description(domain.getDescription())
                .totalAmount(domain.getTotalAmount())
                .currency(domain.getCurrency())
                .correlationId(domain.getCorrelationId())
                .postedAt(domain.getPostedAt())
                .build();
    }
}
