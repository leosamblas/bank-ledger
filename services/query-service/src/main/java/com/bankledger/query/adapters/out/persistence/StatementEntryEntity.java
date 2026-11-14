package com.bankledger.query.adapters.out.persistence;

import com.bankledger.query.domain.model.StatementEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "statement_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementEntryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "sequence", nullable = false)
    private long sequence;

    @Column(name = "journal_id", nullable = false)
    private UUID journalId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "direction", nullable = false, length = 6)
    private String direction;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "counterparty_name", length = 120)
    private String counterpartyName;

    @Column(name = "counterparty_document_masked", length = 20)
    private String counterpartyDocumentMasked;

    @Column(name = "counterparty_account", length = 40)
    private String counterpartyAccount;

    @Column(name = "description", length = 140)
    private String description;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    public static StatementEntryEntity fromDomain(StatementEntry model) {
        return StatementEntryEntity.builder()
                .id(model.id())
                .accountId(model.accountId())
                .sequence(model.sequence())
                .journalId(model.journalId())
                .transactionId(model.transactionId())
                .category(model.category())
                .type(model.type())
                .direction(model.direction())
                .amount(model.amount())
                .balanceAfter(model.balanceAfter())
                .counterpartyName(model.counterpartyName())
                .counterpartyDocumentMasked(model.counterpartyDocumentMasked())
                .counterpartyAccount(model.counterpartyAccount())
                .description(model.description())
                .idempotencyKey(model.idempotencyKey())
                .failureReason(model.failureReason())
                .postedAt(model.postedAt())
                .build();
    }

    public StatementEntry toDomain() {
        return new StatementEntry(
                id,
                accountId,
                sequence,
                journalId,
                transactionId,
                category,
                type,
                direction,
                amount,
                balanceAfter,
                counterpartyName,
                counterpartyDocumentMasked,
                counterpartyAccount,
                description,
                idempotencyKey,
                failureReason,
                postedAt
        );
    }
}
