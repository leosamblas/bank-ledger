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
        Instant createdAt,
        Instant completedAt
) {
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
                tx.getCreatedAt(),
                tx.getCompletedAt()
        );
    }
}
