package com.bankledger.transaction.application.service;

import com.bankledger.transaction.application.port.in.GetTransactionUseCase;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.domain.exception.TransactionNotFoundException;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Application service for querying transactions with authorization enforcement.
 * Enforces OWASP A01 (Broken Object Level Authorization / IDOR) prevention by ensuring
 * that the requesting user matches the transaction creator.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransactionQueryService implements GetTransactionUseCase {

    private final TransactionRepositoryPort transactionRepositoryPort;

    @Override
    public FinancialTransaction getTransaction(UUID transactionId, UUID requestedBy) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");

        FinancialTransaction tx = transactionRepositoryPort.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        if (!tx.getRequestedBy().equals(requestedBy)) {
            // OWASP A01 (BOLA/IDOR protection): Return generic 404 to avoid leaking existence
            throw new TransactionNotFoundException("Transaction not found: " + transactionId);
        }

        return tx;
    }
}
