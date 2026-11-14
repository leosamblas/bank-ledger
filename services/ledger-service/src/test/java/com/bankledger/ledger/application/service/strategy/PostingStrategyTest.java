package com.bankledger.ledger.application.service.strategy;

import com.bankledger.ledger.application.service.CashShardResolver;
import com.bankledger.ledger.domain.command.PostJournalCommand;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalPostingStrategy Unit Tests")
class PostingStrategyTest {

    @Mock
    private CashShardResolver cashShardResolver;

    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");

    private LedgerAccount buildCustomerAccount(UUID id, long balance, long sequence) {
        return LedgerAccount.builder()
                .id(id)
                .code("ACC-" + id.toString().substring(0, 8))
                .kind(AccountKind.CUSTOMER)
                .normalBalance(NormalBalance.CREDIT)
                .currency("BRL")
                .balance(balance)
                .lastSequence(sequence)
                .status(AccountStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private LedgerAccount buildCashAccount(UUID id, long balance, long sequence) {
        return LedgerAccount.builder()
                .id(id)
                .code("SYS-CASH-00")
                .kind(AccountKind.SYSTEM)
                .normalBalance(NormalBalance.DEBIT)
                .currency("BRL")
                .balance(balance)
                .lastSequence(sequence)
                .status(AccountStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    @Nested
    @DisplayName("DepositPostingStrategy Tests")
    class DepositStrategyTests {

        private DepositPostingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new DepositPostingStrategy(cashShardResolver);
        }

        @Test
        @DisplayName("Should support DEPOSIT type case-insensitively")
        void shouldSupportDepositType() {
            assertThat(strategy.supports("DEPOSIT")).isTrue();
            assertThat(strategy.supports("deposit")).isTrue();
            assertThat(strategy.supports("WITHDRAWAL")).isFalse();
            assertThat(strategy.supports("TRANSFER")).isFalse();
            assertThat(strategy.supports(null)).isFalse();
        }

        @Test
        @DisplayName("Should resolve cash shard as accountIdA and customer as accountIdB")
        void shouldResolveAccountsToLock() {
            UUID customerAccountId = UUID.randomUUID();
            UUID transactionId = UUID.randomUUID();
            UUID cashShardId = UUID.randomUUID();

            when(cashShardResolver.resolveShardId("ATM-01", transactionId)).thenReturn(cashShardId);

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "DEPOSIT",
                    10_000L,
                    "BRL",
                    null,
                    customerAccountId,
                    "ATM-01",
                    UUID.randomUUID(),
                    NOW
            );

            PostingAccountsPair pair = strategy.resolveAccountsToLock(command);

            assertThat(pair.accountIdA()).isEqualTo(cashShardId);
            assertThat(pair.accountIdB()).isEqualTo(customerAccountId);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when destinationAccountId is null for deposit")
        void shouldThrowWhenDestinationAccountMissing() {
            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DEPOSIT",
                    10_000L,
                    "BRL",
                    null,
                    null,
                    "ATM-01",
                    UUID.randomUUID(),
                    NOW
            );

            assertThatThrownBy(() -> strategy.resolveAccountsToLock(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Destination account ID is required");
        }

        @Test
        @DisplayName("Should generate DEBIT on cash shard and CREDIT on customer account")
        void shouldGenerateEntriesSuccessfully() {
            UUID journalId = UUID.randomUUID();
            UUID cashId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            LedgerAccount cashAccount = buildCashAccount(cashId, 100_000L, 5L);
            LedgerAccount customerAccount = buildCustomerAccount(customerId, 10_000L, 2L);

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DEPOSIT",
                    5_000L,
                    "BRL",
                    null,
                    customerId,
                    "ATM-01",
                    UUID.randomUUID(),
                    NOW
            );

            PostingEntriesResult result = strategy.generateEntries(command, cashAccount, customerAccount, journalId, NOW);

            assertThat(result.isRejected()).isFalse();
            assertThat(result.rejectionCode()).isNull();
            assertThat(result.rejectionReason()).isNull();
            assertThat(result.entries()).hasSize(2);

            // Cash shard: DEBIT 5_000 -> balance becomes 105_000, sequence 6
            assertThat(cashAccount.getBalance()).isEqualTo(105_000L);
            assertThat(cashAccount.getLastSequence()).isEqualTo(6L);
            var cashEntry = result.entries().get(0);
            assertThat(cashEntry.ledgerAccountId()).isEqualTo(cashId);
            assertThat(cashEntry.direction()).isEqualTo(EntryDirection.DEBIT);
            assertThat(cashEntry.amount()).isEqualTo(5_000L);
            assertThat(cashEntry.balanceAfter()).isEqualTo(105_000L);
            assertThat(cashEntry.sequence()).isEqualTo(6L);
            assertThat(cashEntry.journalId()).isEqualTo(journalId);

            // Customer: CREDIT 5_000 -> balance becomes 15_000, sequence 3
            assertThat(customerAccount.getBalance()).isEqualTo(15_000L);
            assertThat(customerAccount.getLastSequence()).isEqualTo(3L);
            var customerEntry = result.entries().get(1);
            assertThat(customerEntry.ledgerAccountId()).isEqualTo(customerId);
            assertThat(customerEntry.direction()).isEqualTo(EntryDirection.CREDIT);
            assertThat(customerEntry.amount()).isEqualTo(5_000L);
            assertThat(customerEntry.balanceAfter()).isEqualTo(15_000L);
            assertThat(customerEntry.sequence()).isEqualTo(3L);
            assertThat(customerEntry.journalId()).isEqualTo(journalId);

            assertThat(strategy.getJournalDescription(command)).isEqualTo("Depósito contábil");
        }
    }

    @Nested
    @DisplayName("WithdrawalPostingStrategy Tests")
    class WithdrawalStrategyTests {

        private WithdrawalPostingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new WithdrawalPostingStrategy(cashShardResolver);
        }

        @Test
        @DisplayName("Should support WITHDRAWAL type case-insensitively")
        void shouldSupportWithdrawalType() {
            assertThat(strategy.supports("WITHDRAWAL")).isTrue();
            assertThat(strategy.supports("withdrawal")).isTrue();
            assertThat(strategy.supports("DEPOSIT")).isFalse();
            assertThat(strategy.supports("TRANSFER")).isFalse();
            assertThat(strategy.supports(null)).isFalse();
        }

        @Test
        @DisplayName("Should resolve customer as accountIdA and cash shard as accountIdB")
        void shouldResolveAccountsToLock() {
            UUID customerAccountId = UUID.randomUUID();
            UUID transactionId = UUID.randomUUID();
            UUID cashShardId = UUID.randomUUID();

            when(cashShardResolver.resolveShardId("ATM-01", transactionId)).thenReturn(cashShardId);

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    transactionId,
                    "WITHDRAWAL",
                    8_000L,
                    "BRL",
                    customerAccountId,
                    null,
                    "ATM-01",
                    UUID.randomUUID(),
                    NOW
            );

            PostingAccountsPair pair = strategy.resolveAccountsToLock(command);

            assertThat(pair.accountIdA()).isEqualTo(customerAccountId);
            assertThat(pair.accountIdB()).isEqualTo(cashShardId);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when sourceAccountId is null for withdrawal")
        void shouldThrowWhenSourceAccountMissing() {
            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "WITHDRAWAL",
                    8_000L,
                    "BRL",
                    null,
                    null,
                    "ATM-01",
                    UUID.randomUUID(),
                    NOW
            );

            assertThatThrownBy(() -> strategy.resolveAccountsToLock(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Source account ID is required");
        }

        @Test
        @DisplayName("Should generate DEBIT on customer and CREDIT on cash shard when balance is sufficient")
        void shouldGenerateEntriesSuccessfully() {
            UUID journalId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            UUID cashId = UUID.randomUUID();

            LedgerAccount customerAccount = buildCustomerAccount(customerId, 20_000L, 3L);
            LedgerAccount cashAccount = buildCashAccount(cashId, 100_000L, 10L);

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "WITHDRAWAL",
                    8_000L,
                    "BRL",
                    customerId,
                    null,
                    "ATM-01",
                    UUID.randomUUID(),
                    NOW
            );

            PostingEntriesResult result = strategy.generateEntries(command, customerAccount, cashAccount, journalId, NOW);

            assertThat(result.isRejected()).isFalse();
            assertThat(result.entries()).hasSize(2);

            // Customer: DEBIT 8_000 -> balance becomes 12_000, sequence 4
            assertThat(customerAccount.getBalance()).isEqualTo(12_000L);
            assertThat(customerAccount.getLastSequence()).isEqualTo(4L);
            var customerEntry = result.entries().get(0);
            assertThat(customerEntry.ledgerAccountId()).isEqualTo(customerId);
            assertThat(customerEntry.direction()).isEqualTo(EntryDirection.DEBIT);
            assertThat(customerEntry.amount()).isEqualTo(8_000L);
            assertThat(customerEntry.balanceAfter()).isEqualTo(12_000L);

            // Cash: CREDIT 8_000 -> balance becomes 92_000, sequence 11
            assertThat(cashAccount.getBalance()).isEqualTo(92_000L);
            assertThat(cashAccount.getLastSequence()).isEqualTo(11L);
            var cashEntry = result.entries().get(1);
            assertThat(cashEntry.ledgerAccountId()).isEqualTo(cashId);
            assertThat(cashEntry.direction()).isEqualTo(EntryDirection.CREDIT);
            assertThat(cashEntry.amount()).isEqualTo(8_000L);
            assertThat(cashEntry.balanceAfter()).isEqualTo(92_000L);

            assertThat(strategy.getJournalDescription(command)).isEqualTo("Saque contábil");
        }

        @Test
        @DisplayName("Should return rejection result when customer has insufficient funds")
        void shouldRejectWhenFundsInsufficient() {
            UUID journalId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            UUID cashId = UUID.randomUUID();

            LedgerAccount customerAccount = buildCustomerAccount(customerId, 3_000L, 1L);
            LedgerAccount cashAccount = buildCashAccount(cashId, 100_000L, 5L);

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "WITHDRAWAL",
                    10_000L,
                    "BRL",
                    customerId,
                    null,
                    "ATM-01",
                    UUID.randomUUID(),
                    NOW
            );

            PostingEntriesResult result = strategy.generateEntries(command, customerAccount, cashAccount, journalId, NOW);

            assertThat(result.isRejected()).isTrue();
            assertThat(result.rejectionCode()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(result.rejectionReason()).isEqualTo("Saldo insuficiente para efetuar o saque.");
            assertThat(result.entries()).isEmpty();

            // Customer and cash accounts remain unmodified
            assertThat(customerAccount.getBalance()).isEqualTo(3_000L);
            assertThat(customerAccount.getLastSequence()).isEqualTo(1L);
            assertThat(cashAccount.getBalance()).isEqualTo(100_000L);
            assertThat(cashAccount.getLastSequence()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("TransferPostingStrategy Tests")
    class TransferStrategyTests {

        private TransferPostingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new TransferPostingStrategy();
        }

        @Test
        @DisplayName("Should support TRANSFER type case-insensitively")
        void shouldSupportTransferType() {
            assertThat(strategy.supports("TRANSFER")).isTrue();
            assertThat(strategy.supports("transfer")).isTrue();
            assertThat(strategy.supports("DEPOSIT")).isFalse();
            assertThat(strategy.supports("WITHDRAWAL")).isFalse();
            assertThat(strategy.supports(null)).isFalse();
        }

        @Test
        @DisplayName("Should resolve source as accountIdA and destination as accountIdB")
        void shouldResolveAccountsToLock() {
            UUID sourceId = UUID.randomUUID();
            UUID destId = UUID.randomUUID();

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "TRANSFER",
                    15_000L,
                    "BRL",
                    sourceId,
                    destId,
                    null,
                    UUID.randomUUID(),
                    NOW
            );

            PostingAccountsPair pair = strategy.resolveAccountsToLock(command);

            assertThat(pair.accountIdA()).isEqualTo(sourceId);
            assertThat(pair.accountIdB()).isEqualTo(destId);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when source or destination is missing")
        void shouldThrowWhenAccountsMissing() {
            UUID validId = UUID.randomUUID();

            PostJournalCommand noSource = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "TRANSFER",
                    5_000L,
                    "BRL",
                    null,
                    validId,
                    null,
                    UUID.randomUUID(),
                    NOW
            );
            assertThatThrownBy(() -> strategy.resolveAccountsToLock(noSource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Source account ID is required");

            PostJournalCommand noDest = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "TRANSFER",
                    5_000L,
                    "BRL",
                    validId,
                    null,
                    null,
                    UUID.randomUUID(),
                    NOW
            );
            assertThatThrownBy(() -> strategy.resolveAccountsToLock(noDest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Destination account ID is required");
        }

        @Test
        @DisplayName("Should generate DEBIT on source and CREDIT on destination when balance is sufficient")
        void shouldGenerateEntriesSuccessfully() {
            UUID journalId = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID();
            UUID destId = UUID.randomUUID();

            LedgerAccount sourceAccount = buildCustomerAccount(sourceId, 50_000L, 1L);
            LedgerAccount destAccount = buildCustomerAccount(destId, 20_000L, 5L);

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "TRANSFER",
                    15_000L,
                    "BRL",
                    sourceId,
                    destId,
                    null,
                    UUID.randomUUID(),
                    NOW
            );

            PostingEntriesResult result = strategy.generateEntries(command, sourceAccount, destAccount, journalId, NOW);

            assertThat(result.isRejected()).isFalse();
            assertThat(result.entries()).hasSize(2);

            // Source: DEBIT 15_000 -> 35_000, sequence 2
            assertThat(sourceAccount.getBalance()).isEqualTo(35_000L);
            assertThat(sourceAccount.getLastSequence()).isEqualTo(2L);
            var sourceEntry = result.entries().get(0);
            assertThat(sourceEntry.ledgerAccountId()).isEqualTo(sourceId);
            assertThat(sourceEntry.direction()).isEqualTo(EntryDirection.DEBIT);
            assertThat(sourceEntry.amount()).isEqualTo(15_000L);
            assertThat(sourceEntry.balanceAfter()).isEqualTo(35_000L);

            // Dest: CREDIT 15_000 -> 35_000, sequence 6
            assertThat(destAccount.getBalance()).isEqualTo(35_000L);
            assertThat(destAccount.getLastSequence()).isEqualTo(6L);
            var destEntry = result.entries().get(1);
            assertThat(destEntry.ledgerAccountId()).isEqualTo(destId);
            assertThat(destEntry.direction()).isEqualTo(EntryDirection.CREDIT);
            assertThat(destEntry.amount()).isEqualTo(15_000L);
            assertThat(destEntry.balanceAfter()).isEqualTo(35_000L);

            assertThat(strategy.getJournalDescription(command)).isEqualTo("Transferência contábil");
        }

        @Test
        @DisplayName("Should return rejection result when source account has insufficient funds")
        void shouldRejectWhenSourceFundsInsufficient() {
            UUID journalId = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID();
            UUID destId = UUID.randomUUID();

            LedgerAccount sourceAccount = buildCustomerAccount(sourceId, 5_000L, 2L);
            LedgerAccount destAccount = buildCustomerAccount(destId, 20_000L, 5L);

            PostJournalCommand command = new PostJournalCommand(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "TRANSFER",
                    15_000L,
                    "BRL",
                    sourceId,
                    destId,
                    null,
                    UUID.randomUUID(),
                    NOW
            );

            PostingEntriesResult result = strategy.generateEntries(command, sourceAccount, destAccount, journalId, NOW);

            assertThat(result.isRejected()).isTrue();
            assertThat(result.rejectionCode()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(result.rejectionReason()).isEqualTo("Saldo insuficiente para efetuar a transferência.");
            assertThat(result.entries()).isEmpty();

            // Accounts remain unmodified
            assertThat(sourceAccount.getBalance()).isEqualTo(5_000L);
            assertThat(sourceAccount.getLastSequence()).isEqualTo(2L);
            assertThat(destAccount.getBalance()).isEqualTo(20_000L);
            assertThat(destAccount.getLastSequence()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("Record Value Objects Tests")
    class RecordTests {

        @Test
        @DisplayName("PostingAccountsPair should reject null IDs")
        void shouldValidatePostingAccountsPair() {
            UUID id = UUID.randomUUID();
            assertThatThrownBy(() -> new PostingAccountsPair(null, id))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new PostingAccountsPair(id, null))
                    .isInstanceOf(NullPointerException.class);

            PostingAccountsPair pair = new PostingAccountsPair(id, id);
            assertThat(pair.accountIdA()).isEqualTo(id);
            assertThat(pair.accountIdB()).isEqualTo(id);
        }

        @Test
        @DisplayName("PostingEntriesResult helper methods should behave correctly")
        void shouldValidatePostingEntriesResult() {
            PostingEntriesResult success = PostingEntriesResult.success(List.of());
            assertThat(success.isRejected()).isFalse();
            assertThat(success.entries()).isEmpty();
            assertThat(success.rejectionCode()).isNull();

            PostingEntriesResult rejected = PostingEntriesResult.rejected("ERR_01", "Some error");
            assertThat(rejected.isRejected()).isTrue();
            assertThat(rejected.rejectionCode()).isEqualTo("ERR_01");
            assertThat(rejected.rejectionReason()).isEqualTo("Some error");
            assertThat(rejected.entries()).isEmpty();
        }
    }
}
