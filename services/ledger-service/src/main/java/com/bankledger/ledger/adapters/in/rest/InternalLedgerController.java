package com.bankledger.ledger.adapters.in.rest;

import com.bankledger.ledger.adapters.in.rest.dto.JournalEntryResponse;
import com.bankledger.ledger.adapters.in.rest.dto.JournalResponse;
import com.bankledger.ledger.adapters.in.rest.dto.LedgerBalanceResponse;
import com.bankledger.ledger.adapters.in.rest.dto.TrialBalanceResponse;
import com.bankledger.ledger.application.port.in.GetJournalUseCase;
import com.bankledger.ledger.application.port.in.GetLedgerAccountUseCase;
import com.bankledger.ledger.application.port.in.GetTrialBalanceUseCase;
import com.bankledger.ledger.application.port.in.TrialBalanceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Internal REST controller exposing ledger account balances, journals, and trial balance.
 * Implements endpoints specified in {@code api-contracts/ledger-service.yaml}.
 */
@RestController
@RequestMapping("/internal/v1/ledger")
@RequiredArgsConstructor
public class InternalLedgerController {

    private final GetLedgerAccountUseCase getLedgerAccountUseCase;
    private final GetJournalUseCase getJournalUseCase;
    private final GetTrialBalanceUseCase getTrialBalanceUseCase;

    @GetMapping("/accounts/{id}/balance")
    public ResponseEntity<LedgerBalanceResponse> getAccountBalance(@PathVariable UUID id) {
        return getLedgerAccountUseCase.getAccount(id)
                .map(account -> new LedgerBalanceResponse(
                        account.getId(),
                        account.getBalance(),
                        account.getLastSequence(),
                        account.getStatus().name()
                ))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/journals/by-transaction/{transactionId}")
    public ResponseEntity<JournalResponse> getJournalByTransaction(@PathVariable UUID transactionId) {
        return getJournalUseCase.getJournalByTransactionId(transactionId)
                .map(journal -> {
                    List<JournalEntryResponse> entryResponses = journal.getEntries().stream()
                            .map(entry -> new JournalEntryResponse(
                                    entry.ledgerAccountId(),
                                    entry.direction().name(),
                                    entry.amount(),
                                    entry.balanceAfter(),
                                    entry.sequence()
                            ))
                            .toList();

                    return new JournalResponse(
                            journal.getId(),
                            journal.getTransactionId(),
                            journal.getType(),
                            journal.getStatus().name(),
                            journal.getTotalAmount(),
                            entryResponses
                    );
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceResponse> getTrialBalance() {
        TrialBalanceResult result = getTrialBalanceUseCase.getTrialBalance();
        return ResponseEntity.ok(new TrialBalanceResponse(
                result.debits(),
                result.credits(),
                result.difference()
        ));
    }
}
