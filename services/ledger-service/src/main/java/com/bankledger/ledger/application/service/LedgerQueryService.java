package com.bankledger.ledger.application.service;

import com.bankledger.ledger.application.port.in.GetJournalUseCase;
import com.bankledger.ledger.application.port.in.GetLedgerAccountUseCase;
import com.bankledger.ledger.application.port.in.GetTrialBalanceUseCase;
import com.bankledger.ledger.application.port.in.TrialBalanceResult;
import com.bankledger.ledger.application.port.out.JournalRepositoryPort;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.LedgerAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Application query service providing read-only access to accounts, journals, and trial balance.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LedgerQueryService implements GetLedgerAccountUseCase, GetJournalUseCase, GetTrialBalanceUseCase {

    private final LedgerAccountRepositoryPort ledgerAccountRepositoryPort;
    private final JournalRepositoryPort journalRepositoryPort;

    @Override
    public Optional<LedgerAccount> getAccount(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return ledgerAccountRepositoryPort.findById(id);
    }

    @Override
    public Optional<Journal> getJournalByTransactionId(UUID transactionId) {
        if (transactionId == null) {
            return Optional.empty();
        }
        return journalRepositoryPort.findByTransactionId(transactionId);
    }

    @Override
    public TrialBalanceResult getTrialBalance() {
        long debits = ledgerAccountRepositoryPort.sumDebits();
        long credits = ledgerAccountRepositoryPort.sumCredits();
        long difference = debits - credits;

        log.debug("Computed trial balance: debits={}, credits={}, difference={}", debits, credits, difference);
        return new TrialBalanceResult(debits, credits, difference);
    }
}
