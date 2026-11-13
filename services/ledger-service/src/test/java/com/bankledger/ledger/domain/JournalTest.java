package com.bankledger.ledger.domain;

import com.bankledger.ledger.domain.exception.UnbalancedJournalException;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.JournalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Journal - Double-Entry Ledger Engine Unit Tests")
class JournalTest {

    private final UUID journalId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();
    private final UUID cashAccountId = UUID.randomUUID();
    private final UUID customerAccountId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-13T12:00:00Z");

    @Nested
    @DisplayName("Posted Journal Validation")
    class PostedJournalTests {

        @Test
        @DisplayName("Valid double-entry (1 DEBIT, 1 CREDIT) creates POSTED journal")
        void validTwoLegsDeposit() {
            List<Entry> entries = List.of(
                    new Entry(UUID.randomUUID(), journalId, cashAccountId, EntryDirection.DEBIT, 10_000L, 10_000L, 1L, now),
                    new Entry(UUID.randomUUID(), journalId, customerAccountId, EntryDirection.CREDIT, 10_000L, 10_000L, 1L, now)
            );

            Journal journal = Journal.createPosted(
                    journalId,
                    transactionId,
                    "DEPOSIT",
                    "Cash Deposit",
                    10_000L,
                    "BRL",
                    correlationId,
                    now,
                    entries
            );

            assertThat(journal.getId()).isEqualTo(journalId);
            assertThat(journal.getTransactionId()).isEqualTo(transactionId);
            assertThat(journal.getType()).isEqualTo("DEPOSIT");
            assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
            assertThat(journal.getRejectCode()).isNull();
            assertThat(journal.getRejectReason()).isNull();
            assertThat(journal.getDescription()).isEqualTo("Cash Deposit");
            assertThat(journal.getTotalAmount()).isEqualTo(10_000L);
            assertThat(journal.getCurrency()).isEqualTo("BRL");
            assertThat(journal.getCorrelationId()).isEqualTo(correlationId);
            assertThat(journal.getPostedAt()).isEqualTo(now);
            assertThat(journal.getEntries()).hasSize(2);
            assertThat(journal.getEntries()).containsExactlyElementsOf(entries);
        }

        @Test
        @DisplayName("Multi-leg balanced journal creates POSTED journal")
        void validMultiLegJournal() {
            UUID feeAccountId = UUID.randomUUID();

            // Total withdrawal = 10_000: Customer debited 10_000, Cash credited 9_900, Fee credited 100
            List<Entry> entries = List.of(
                    new Entry(UUID.randomUUID(), journalId, customerAccountId, EntryDirection.DEBIT, 10_000L, 0L, 2L, now),
                    new Entry(UUID.randomUUID(), journalId, cashAccountId, EntryDirection.CREDIT, 9_900L, 100L, 5L, now),
                    new Entry(UUID.randomUUID(), journalId, feeAccountId, EntryDirection.CREDIT, 100L, 100L, 1L, now)
            );

            Journal journal = Journal.createPosted(
                    journalId,
                    transactionId,
                    "WITHDRAWAL",
                    "ATM Withdrawal with fee",
                    10_000L,
                    "BRL",
                    correlationId,
                    now,
                    entries
            );

            assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
            assertThat(journal.getEntries()).hasSize(3);
        }

        @Test
        @DisplayName("Entries list is defensively copied and immutable")
        void entriesListIsImmutable() {
            List<Entry> mutableEntries = new ArrayList<>();
            mutableEntries.add(new Entry(UUID.randomUUID(), journalId, cashAccountId, EntryDirection.DEBIT, 1_000L, 1_000L, 1L, now));
            mutableEntries.add(new Entry(UUID.randomUUID(), journalId, customerAccountId, EntryDirection.CREDIT, 1_000L, 1_000L, 1L, now));

            Journal journal = Journal.createPosted(
                    journalId,
                    transactionId,
                    "DEPOSIT",
                    "Deposit",
                    1_000L,
                    "BRL",
                    correlationId,
                    now,
                    mutableEntries
            );

            Entry extraEntry = new Entry(UUID.randomUUID(), journalId, cashAccountId, EntryDirection.DEBIT, 500L, 1_500L, 2L, now);
            mutableEntries.add(extraEntry);

            assertThat(journal.getEntries()).hasSize(2);
            assertThatThrownBy(() -> journal.getEntries().add(extraEntry))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Unbalanced and Invalid Journal Rejections")
    class UnbalancedJournalTests {

        @Test
        @DisplayName("Debits != Credits throws UnbalancedJournalException")
        void debitsNotEqualCreditsThrows() {
            List<Entry> entries = List.of(
                    new Entry(UUID.randomUUID(), journalId, cashAccountId, EntryDirection.DEBIT, 10_000L, 10_000L, 1L, now),
                    new Entry(UUID.randomUUID(), journalId, customerAccountId, EntryDirection.CREDIT, 8_000L, 8_000L, 1L, now)
            );

            assertThatThrownBy(() -> Journal.createPosted(
                    journalId,
                    transactionId,
                    "DEPOSIT",
                    "Unbalanced Deposit",
                    10_000L,
                    "BRL",
                    correlationId,
                    now,
                    entries
            ))
                    .isInstanceOf(UnbalancedJournalException.class)
                    .hasMessageContaining("10000")
                    .hasMessageContaining("8000");
        }

        @Test
        @DisplayName("Debits == Credits != totalAmount throws UnbalancedJournalException")
        void sumDoesNotMatchTotalAmountThrows() {
            List<Entry> entries = List.of(
                    new Entry(UUID.randomUUID(), journalId, cashAccountId, EntryDirection.DEBIT, 10_000L, 10_000L, 1L, now),
                    new Entry(UUID.randomUUID(), journalId, customerAccountId, EntryDirection.CREDIT, 10_000L, 10_000L, 1L, now)
            );

            assertThatThrownBy(() -> Journal.createPosted(
                    journalId,
                    transactionId,
                    "DEPOSIT",
                    "Mismatched Total",
                    5_000L, // Expected 10_000L
                    "BRL",
                    correlationId,
                    now,
                    entries
            ))
                    .isInstanceOf(UnbalancedJournalException.class)
                    .hasMessageContaining("5000")
                    .hasMessageContaining("10000");
        }

        @Test
        @DisplayName("Journal with fewer than 2 entries throws UnbalancedJournalException")
        void singleEntryThrows() {
            List<Entry> singleEntry = List.of(
                    new Entry(UUID.randomUUID(), journalId, cashAccountId, EntryDirection.DEBIT, 10_000L, 10_000L, 1L, now)
            );

            assertThatThrownBy(() -> Journal.createPosted(
                    journalId,
                    transactionId,
                    "DEPOSIT",
                    "Single Leg",
                    10_000L,
                    "BRL",
                    correlationId,
                    now,
                    singleEntry
            ))
                    .isInstanceOf(UnbalancedJournalException.class)
                    .hasMessageContaining("at least 2 entries");
        }

        @Test
        @DisplayName("Journal with null or empty entries throws IllegalArgumentException")
        void nullOrEmptyEntriesThrows() {
            assertThatThrownBy(() -> Journal.createPosted(
                    journalId,
                    transactionId,
                    "DEPOSIT",
                    "Null legs",
                    10_000L,
                    "BRL",
                    correlationId,
                    now,
                    null
            )).isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> Journal.createPosted(
                    journalId,
                    transactionId,
                    "DEPOSIT",
                    "Empty legs",
                    10_000L,
                    "BRL",
                    correlationId,
                    now,
                    List.of()
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -10_000L})
        @DisplayName("Total amount <= 0 throws IllegalArgumentException")
        void nonPositiveTotalAmountThrows(long invalidAmount) {
            List<Entry> entries = List.of(
                    new Entry(UUID.randomUUID(), journalId, cashAccountId, EntryDirection.DEBIT, 10_000L, 10_000L, 1L, now),
                    new Entry(UUID.randomUUID(), journalId, customerAccountId, EntryDirection.CREDIT, 10_000L, 10_000L, 1L, now)
            );

            assertThatThrownBy(() -> Journal.createPosted(
                    journalId,
                    transactionId,
                    "DEPOSIT",
                    "Invalid Amount",
                    invalidAmount,
                    "BRL",
                    correlationId,
                    now,
                    entries
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Rejected Journal Validation")
    class RejectedJournalTests {

        @Test
        @DisplayName("createRejected creates REJECTED journal with rejectCode, rejectReason and empty entries")
        void rejectedJournalFactory() {
            Journal journal = Journal.createRejected(
                    journalId,
                    transactionId,
                    "WITHDRAWAL",
                    "ATM Withdrawal rejected",
                    50_000L,
                    "BRL",
                    correlationId,
                    now,
                    "INSUFFICIENT_FUNDS",
                    "Customer balance is lower than withdrawal amount"
            );

            assertThat(journal.getId()).isEqualTo(journalId);
            assertThat(journal.getTransactionId()).isEqualTo(transactionId);
            assertThat(journal.getType()).isEqualTo("WITHDRAWAL");
            assertThat(journal.getStatus()).isEqualTo(JournalStatus.REJECTED);
            assertThat(journal.getRejectCode()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(journal.getRejectReason()).isEqualTo("Customer balance is lower than withdrawal amount");
            assertThat(journal.getDescription()).isEqualTo("ATM Withdrawal rejected");
            assertThat(journal.getTotalAmount()).isEqualTo(50_000L);
            assertThat(journal.getCurrency()).isEqualTo("BRL");
            assertThat(journal.getCorrelationId()).isEqualTo(correlationId);
            assertThat(journal.getPostedAt()).isEqualTo(now);
            assertThat(journal.getEntries()).isEmpty();
        }
    }
}
