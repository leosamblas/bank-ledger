package com.bankledger.ledger.domain;

import com.bankledger.ledger.domain.exception.InsufficientFundsException;
import com.bankledger.ledger.domain.exception.LedgerAccountBlockedException;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.EntryApplicationResult;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LedgerAccount - Domain Unit Tests")
class LedgerAccountTest {

    private LedgerAccount createCustomerAccount(long initialBalance, long initialSequence) {
        return LedgerAccount.builder()
                .id(UUID.randomUUID())
                .code("ACC-0001-100000-1")
                .kind(AccountKind.CUSTOMER)
                .normalBalance(NormalBalance.CREDIT)
                .currency("BRL")
                .balance(initialBalance)
                .lastSequence(initialSequence)
                .status(AccountStatus.ACTIVE)
                .createdAt(Instant.parse("2026-11-13T10:00:00Z"))
                .updatedAt(Instant.parse("2026-11-13T10:00:00Z"))
                .version(0L)
                .build();
    }

    private LedgerAccount createCashAccount(long initialBalance, long initialSequence) {
        return LedgerAccount.builder()
                .id(UUID.randomUUID())
                .code("SYS-CASH-00")
                .kind(AccountKind.SYSTEM)
                .normalBalance(NormalBalance.DEBIT)
                .currency("BRL")
                .balance(initialBalance)
                .lastSequence(initialSequence)
                .status(AccountStatus.ACTIVE)
                .createdAt(Instant.parse("2026-11-13T10:00:00Z"))
                .updatedAt(Instant.parse("2026-11-13T10:00:00Z"))
                .version(0L)
                .build();
    }

    @Nested
    @DisplayName("Customer Account (Normal Balance CREDIT)")
    class CustomerAccountTests {

        @Test
        @DisplayName("CREDIT entry increases balance and increments sequence")
        void creditIncreasesBalance() {
            LedgerAccount account = createCustomerAccount(1_000L, 0L);

            EntryApplicationResult result = account.applyEntry(EntryDirection.CREDIT, 500L);

            assertThat(result.balanceAfter()).isEqualTo(1_500L);
            assertThat(result.sequence()).isEqualTo(1L);
            assertThat(account.getBalance()).isEqualTo(1_500L);
            assertThat(account.getLastSequence()).isEqualTo(1L);
            assertThat(account.getUpdatedAt()).isAfter(Instant.parse("2026-11-13T10:00:00Z"));
        }

        @Test
        @DisplayName("DEBIT entry decreases balance and increments sequence")
        void debitDecreasesBalance() {
            LedgerAccount account = createCustomerAccount(1_000L, 3L);

            EntryApplicationResult result = account.applyEntry(EntryDirection.DEBIT, 400L);

            assertThat(result.balanceAfter()).isEqualTo(600L);
            assertThat(result.sequence()).isEqualTo(4L);
            assertThat(account.getBalance()).isEqualTo(600L);
            assertThat(account.getLastSequence()).isEqualTo(4L);
        }

        @Test
        @DisplayName("DEBIT bringing balance exactly to zero is allowed")
        void debitExactBalanceAllowed() {
            LedgerAccount account = createCustomerAccount(500L, 1L);

            EntryApplicationResult result = account.applyEntry(EntryDirection.DEBIT, 500L);

            assertThat(result.balanceAfter()).isEqualTo(0L);
            assertThat(result.sequence()).isEqualTo(2L);
            assertThat(account.getBalance()).isEqualTo(0L);
        }

        @Test
        @DisplayName("DEBIT exceeding customer balance throws InsufficientFundsException and preserves state")
        void debitExceedingBalanceThrows() {
            LedgerAccount account = createCustomerAccount(500L, 2L);
            Instant beforeUpdate = account.getUpdatedAt();

            assertThatThrownBy(() -> account.applyEntry(EntryDirection.DEBIT, 501L))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("500")
                    .hasMessageContaining("501");

            assertThat(account.getBalance()).isEqualTo(500L);
            assertThat(account.getLastSequence()).isEqualTo(2L);
            assertThat(account.getUpdatedAt()).isEqualTo(beforeUpdate);
        }
    }

    @Nested
    @DisplayName("System Cash Account (Normal Balance DEBIT)")
    class SystemCashAccountTests {

        @Test
        @DisplayName("DEBIT entry increases cash asset balance")
        void debitIncreasesCashAsset() {
            LedgerAccount account = createCashAccount(10_000L, 5L);

            EntryApplicationResult result = account.applyEntry(EntryDirection.DEBIT, 2_500L);

            assertThat(result.balanceAfter()).isEqualTo(12_500L);
            assertThat(result.sequence()).isEqualTo(6L);
            assertThat(account.getBalance()).isEqualTo(12_500L);
            assertThat(account.getLastSequence()).isEqualTo(6L);
        }

        @Test
        @DisplayName("CREDIT entry decreases cash asset balance")
        void creditDecreasesCashAsset() {
            LedgerAccount account = createCashAccount(10_000L, 10L);

            EntryApplicationResult result = account.applyEntry(EntryDirection.CREDIT, 3_000L);

            assertThat(result.balanceAfter()).isEqualTo(7_000L);
            assertThat(result.sequence()).isEqualTo(11L);
            assertThat(account.getBalance()).isEqualTo(7_000L);
            assertThat(account.getLastSequence()).isEqualTo(11L);
        }

        @Test
        @DisplayName("System account allows balance flexibility (can go negative without InsufficientFundsException)")
        void systemAccountAllowsNegativeBalance() {
            LedgerAccount account = createCashAccount(100L, 0L);

            EntryApplicationResult result = account.applyEntry(EntryDirection.CREDIT, 500L);

            assertThat(result.balanceAfter()).isEqualTo(-400L);
            assertThat(result.sequence()).isEqualTo(1L);
            assertThat(account.getBalance()).isEqualTo(-400L);
        }
    }

    @Nested
    @DisplayName("Input Validation and Account Status")
    class ValidationAndStatusTests {

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -100L})
        @DisplayName("Amount <= 0 throws IllegalArgumentException")
        void nonPositiveAmountThrows(long invalidAmount) {
            LedgerAccount account = createCustomerAccount(1_000L, 0L);

            assertThatThrownBy(() -> account.applyEntry(EntryDirection.CREDIT, invalidAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");

            assertThat(account.getBalance()).isEqualTo(1_000L);
            assertThat(account.getLastSequence()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Null direction throws NullPointerException")
        void nullDirectionThrows() {
            LedgerAccount account = createCustomerAccount(1_000L, 0L);

            assertThatThrownBy(() -> account.applyEntry(null, 100L))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Blocked account throws LedgerAccountBlockedException")
        void blockedAccountThrows() {
            LedgerAccount account = LedgerAccount.builder()
                    .id(UUID.randomUUID())
                    .code("ACC-BLOCKED")
                    .kind(AccountKind.CUSTOMER)
                    .normalBalance(NormalBalance.CREDIT)
                    .currency("BRL")
                    .balance(1_000L)
                    .lastSequence(0L)
                    .status(AccountStatus.BLOCKED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0L)
                    .build();

            assertThatThrownBy(() -> account.applyEntry(EntryDirection.CREDIT, 100L))
                    .isInstanceOf(LedgerAccountBlockedException.class)
                    .hasMessageContaining("BLOCKED");
        }

        @Test
        @DisplayName("Closed account throws LedgerAccountBlockedException")
        void closedAccountThrows() {
            LedgerAccount account = LedgerAccount.builder()
                    .id(UUID.randomUUID())
                    .code("ACC-CLOSED")
                    .kind(AccountKind.CUSTOMER)
                    .normalBalance(NormalBalance.CREDIT)
                    .currency("BRL")
                    .balance(0L)
                    .lastSequence(0L)
                    .status(AccountStatus.CLOSED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0L)
                    .build();

            assertThatThrownBy(() -> account.applyEntry(EntryDirection.DEBIT, 100L))
                    .isInstanceOf(LedgerAccountBlockedException.class)
                    .hasMessageContaining("CLOSED");
        }

        @Test
        @DisplayName("Factory createCustomerInitial initializes active account with zero balance and BRL currency")
        void factoryCreateCustomerInitial() {
            UUID id = UUID.randomUUID();
            LedgerAccount account = LedgerAccount.createCustomerInitial(id, "ACC-0001-200000-1");

            assertThat(account.getId()).isEqualTo(id);
            assertThat(account.getCode()).isEqualTo("ACC-0001-200000-1");
            assertThat(account.getKind()).isEqualTo(AccountKind.CUSTOMER);
            assertThat(account.getNormalBalance()).isEqualTo(NormalBalance.CREDIT);
            assertThat(account.getCurrency()).isEqualTo("BRL");
            assertThat(account.getBalance()).isEqualTo(0L);
            assertThat(account.getLastSequence()).isEqualTo(0L);
            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.getCreatedAt()).isNotNull();
            assertThat(account.getUpdatedAt()).isNotNull();
            assertThat(account.getVersion()).isEqualTo(0L);
        }
    }
}
