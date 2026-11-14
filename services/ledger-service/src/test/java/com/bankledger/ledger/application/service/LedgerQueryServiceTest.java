package com.bankledger.ledger.application.service;

import com.bankledger.ledger.application.port.in.TrialBalanceResult;
import com.bankledger.ledger.application.port.out.JournalRepositoryPort;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import com.bankledger.ledger.domain.model.TrialBalanceTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerQueryService Unit Tests")
class LedgerQueryServiceTest {

    @Mock
    private LedgerAccountRepositoryPort ledgerAccountRepositoryPort;

    @Mock
    private JournalRepositoryPort journalRepositoryPort;

    @InjectMocks
    private LedgerQueryService ledgerQueryService;

    @Nested
    @DisplayName("getTrialBalance Tests")
    class GetTrialBalanceTests {

        @Test
        @DisplayName("Should return balanced trial balance when debits equal credits")
        void shouldReturnBalancedTrialBalance() {
            TrialBalanceTotals totals = new TrialBalanceTotals(50000L, 50000L);
            when(ledgerAccountRepositoryPort.getTrialBalanceTotals()).thenReturn(totals);

            TrialBalanceResult result = ledgerQueryService.getTrialBalance();

            assertThat(result).isNotNull();
            assertThat(result.debits()).isEqualTo(50000L);
            assertThat(result.credits()).isEqualTo(50000L);
            assertThat(result.difference()).isEqualTo(0L);
            verify(ledgerAccountRepositoryPort).getTrialBalanceTotals();
        }

        @Test
        @DisplayName("Should return positive difference when debits exceed credits")
        void shouldReturnPositiveDifferenceWhenDebitsExceedCredits() {
            TrialBalanceTotals totals = new TrialBalanceTotals(60000L, 45000L);
            when(ledgerAccountRepositoryPort.getTrialBalanceTotals()).thenReturn(totals);

            TrialBalanceResult result = ledgerQueryService.getTrialBalance();

            assertThat(result).isNotNull();
            assertThat(result.debits()).isEqualTo(60000L);
            assertThat(result.credits()).isEqualTo(45000L);
            assertThat(result.difference()).isEqualTo(15000L);
            verify(ledgerAccountRepositoryPort).getTrialBalanceTotals();
        }

        @Test
        @DisplayName("Should return negative difference when credits exceed debits")
        void shouldReturnNegativeDifferenceWhenCreditsExceedDebits() {
            TrialBalanceTotals totals = new TrialBalanceTotals(30000L, 50000L);
            when(ledgerAccountRepositoryPort.getTrialBalanceTotals()).thenReturn(totals);

            TrialBalanceResult result = ledgerQueryService.getTrialBalance();

            assertThat(result).isNotNull();
            assertThat(result.debits()).isEqualTo(30000L);
            assertThat(result.credits()).isEqualTo(50000L);
            assertThat(result.difference()).isEqualTo(-20000L);
            verify(ledgerAccountRepositoryPort).getTrialBalanceTotals();
        }

        @Test
        @DisplayName("Should return zero trial balance when ledger has no activity")
        void shouldReturnZeroTrialBalanceWhenLedgerIsEmpty() {
            TrialBalanceTotals totals = new TrialBalanceTotals(0L, 0L);
            when(ledgerAccountRepositoryPort.getTrialBalanceTotals()).thenReturn(totals);

            TrialBalanceResult result = ledgerQueryService.getTrialBalance();

            assertThat(result).isNotNull();
            assertThat(result.debits()).isZero();
            assertThat(result.credits()).isZero();
            assertThat(result.difference()).isZero();
            verify(ledgerAccountRepositoryPort).getTrialBalanceTotals();
        }
    }

    @Nested
    @DisplayName("getAccount Tests")
    class GetAccountTests {

        @Test
        @DisplayName("Should return account when found by ID")
        void shouldReturnAccountWhenFound() {
            UUID accountId = UUID.randomUUID();
            LedgerAccount account = new LedgerAccount(
                    accountId,
                    "ACC-0001-100000-1",
                    AccountKind.CUSTOMER,
                    NormalBalance.CREDIT,
                    "BRL",
                    100000L,
                    1L,
                    AccountStatus.ACTIVE,
                    Instant.now(),
                    Instant.now(),
                    0L
            );
            when(ledgerAccountRepositoryPort.findById(accountId)).thenReturn(Optional.of(account));

            Optional<LedgerAccount> result = ledgerQueryService.getAccount(accountId);

            assertThat(result).isPresent().contains(account);
            verify(ledgerAccountRepositoryPort).findById(accountId);
        }

        @Test
        @DisplayName("Should return empty when account not found by ID")
        void shouldReturnEmptyWhenNotFound() {
            UUID accountId = UUID.randomUUID();
            when(ledgerAccountRepositoryPort.findById(accountId)).thenReturn(Optional.empty());

            Optional<LedgerAccount> result = ledgerQueryService.getAccount(accountId);

            assertThat(result).isEmpty();
            verify(ledgerAccountRepositoryPort).findById(accountId);
        }

        @Test
        @DisplayName("Should return empty without calling repository when ID is null")
        void shouldReturnEmptyWhenIdIsNull() {
            Optional<LedgerAccount> result = ledgerQueryService.getAccount(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(ledgerAccountRepositoryPort);
        }
    }

    @Nested
    @DisplayName("getJournalByTransactionId Tests")
    class GetJournalTests {

        @Test
        @DisplayName("Should return journal when found by transaction ID")
        void shouldReturnJournalWhenFound() {
            UUID txId = UUID.randomUUID();
            UUID journalId = UUID.randomUUID();
            UUID accountA = UUID.randomUUID();
            UUID accountB = UUID.randomUUID();

            Entry entryA = new Entry(
                    UUID.randomUUID(), journalId, accountA, EntryDirection.DEBIT, 50000L, 50000L, 1L, Instant.now()
            );
            Entry entryB = new Entry(
                    UUID.randomUUID(), journalId, accountB, EntryDirection.CREDIT, 50000L, 50000L, 1L, Instant.now()
            );

            Journal journal = Journal.createPosted(
                    journalId,
                    txId,
                    "DEPOSIT",
                    "Initial deposit",
                    50000L,
                    "BRL",
                    UUID.randomUUID(),
                    Instant.now(),
                    List.of(entryA, entryB)
            );
            when(journalRepositoryPort.findByTransactionId(txId)).thenReturn(Optional.of(journal));

            Optional<Journal> result = ledgerQueryService.getJournalByTransactionId(txId);

            assertThat(result).isPresent().contains(journal);
            verify(journalRepositoryPort).findByTransactionId(txId);
        }

        @Test
        @DisplayName("Should return empty when journal not found by transaction ID")
        void shouldReturnEmptyWhenNotFound() {
            UUID txId = UUID.randomUUID();
            when(journalRepositoryPort.findByTransactionId(txId)).thenReturn(Optional.empty());

            Optional<Journal> result = ledgerQueryService.getJournalByTransactionId(txId);

            assertThat(result).isEmpty();
            verify(journalRepositoryPort).findByTransactionId(txId);
        }

        @Test
        @DisplayName("Should return empty without calling repository when transaction ID is null")
        void shouldReturnEmptyWhenTxIdIsNull() {
            Optional<Journal> result = ledgerQueryService.getJournalByTransactionId(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(journalRepositoryPort);
        }
    }
}
