package com.bankledger.transaction.adapters.in.rest.dto;

import com.bankledger.transaction.domain.model.FinancialTransaction;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String type,
        String status,
        UUID sourceAccountId,
        UUID destinationAccountId,
        Long amount,
        String currency,
        String failureCode,
        String protocolNumber,
        Instant createdAt,
        Instant completedAt
) {
    public TransactionResponse(
            UUID id,
            String type,
            String status,
            UUID sourceAccountId,
            UUID destinationAccountId,
            Long amount,
            String currency,
            String failureCode,
            Instant createdAt,
            Instant completedAt
    ) {
        this(id, type, status, sourceAccountId, destinationAccountId, amount, currency, failureCode, null, createdAt, completedAt);
    }

    public static TransactionResponse fromDomain(FinancialTransaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getSourceAccountId(),
                tx.getDestinationAccountId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getFailureCode(),
                tx.getProtocolNumber(),
                tx.getCreatedAt(),
                tx.getCompletedAt()
        );
    }
}
