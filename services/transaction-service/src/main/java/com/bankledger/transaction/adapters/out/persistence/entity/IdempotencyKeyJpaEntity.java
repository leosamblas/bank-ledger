package com.bankledger.transaction.adapters.out.persistence.entity;

import com.bankledger.transaction.domain.model.IdempotencyRecord;
import com.bankledger.transaction.domain.model.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.sql.Types;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "idempotency_key")
@IdClass(IdempotencyKeyId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKeyJpaEntity {

    @Id
    @Column(name = "scope", nullable = false)
    private UUID scope;

    @Id
    @Column(name = "idem_key", nullable = false)
    private UUID idemKey;

    @Column(name = "endpoint", nullable = false, length = 40)
    private String endpoint;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IdempotencyStatus status;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static IdempotencyKeyJpaEntity fromDomain(IdempotencyRecord domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        return IdempotencyKeyJpaEntity.builder()
                .scope(domain.scope())
                .idemKey(domain.key())
                .endpoint(domain.endpoint())
                .requestHash(domain.requestHash())
                .status(domain.status())
                .transactionId(domain.transactionId())
                .responseStatus(domain.responseStatus())
                .responseBody(domain.responseBody())
                .createdAt(domain.createdAt())
                .expiresAt(domain.expiresAt())
                .build();
    }

    public IdempotencyRecord toDomain() {
        return new IdempotencyRecord(
                scope,
                idemKey,
                endpoint,
                requestHash,
                status,
                transactionId,
                responseStatus,
                responseBody,
                createdAt,
                expiresAt
        );
    }
}
