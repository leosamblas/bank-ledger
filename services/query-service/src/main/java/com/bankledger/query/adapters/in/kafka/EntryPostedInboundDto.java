package com.bankledger.query.adapters.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Strongly typed inbound DTO representing entry posted events consumed from Kafka.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EntryPostedInboundDto(
        UUID id,
        UUID entryId,
        UUID accountId,
        UUID ledgerAccountId,
        UUID journalId,
        UUID transactionId,
        String direction,
        String entryType,
        long amount,
        long sequence,
        Instant postedAt,
        Instant timestamp,
        Instant createdAt,
        Long balanceAfter,
        String category,
        String type,
        String description,
        String counterpartyName,
        String counterpartyDocumentMasked,
        String counterpartyAccount,
        String idempotencyKey,
        String failureReason
) {
}
