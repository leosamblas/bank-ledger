package com.bankledger.transaction.application.service;

import com.bankledger.transaction.application.port.in.HandleJournalResultUseCase;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.domain.event.JournalResultPayload;
import com.bankledger.transaction.domain.exception.TransactionNotFoundException;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import com.bankledger.transaction.domain.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Application service that processes accounting journal results from ledger-service.
 * Updates the state of the associated {@link FinancialTransaction} to COMPLETED or FAILED.
 * Enforces idempotency: if transaction is already resolved, duplicate results are ignored.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class HandleJournalResultService implements HandleJournalResultUseCase {

    private final TransactionRepositoryPort transactionRepositoryPort;
    private final Clock clock;

    @Override
    public void handleResult(JournalResultPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(payload.transactionId(), "payload transactionId must not be null");

        FinancialTransaction tx = transactionRepositoryPort.findById(payload.transactionId())
                .orElseThrow(() -> {
                    log.error("Transaction not found for journal result: txId={}", payload.transactionId());
                    return new TransactionNotFoundException(payload.transactionId());
                });

        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.info("Transaction {} already resolved with status {}, skipping journal result",
                    tx.getId(), tx.getStatus());
            return;
        }

        Instant now = Instant.now(clock);

        if ("POSTED".equalsIgnoreCase(payload.status())) {
            tx.complete(now);
            log.info("Transaction {} marked COMPLETED based on POSTED journal result", tx.getId());
        } else if ("REJECTED".equalsIgnoreCase(payload.status())) {
            tx.fail(payload.rejectCode(), payload.rejectReason(), now);
            log.warn("Transaction {} marked FAILED: code={}, reason={}",
                    tx.getId(), payload.rejectCode(), payload.rejectReason());
        } else {
            log.error("Unsupported journal result status '{}' for transaction {}", payload.status(), tx.getId());
            throw new IllegalArgumentException("Unsupported journal status: " + payload.status());
        }

        transactionRepositoryPort.save(tx);
    }
}
