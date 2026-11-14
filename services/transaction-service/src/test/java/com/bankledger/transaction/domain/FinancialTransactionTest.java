package com.bankledger.transaction.domain;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.commons.constant.BankingErrorCodes;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import com.bankledger.transaction.domain.model.TransactionStatus;
import com.bankledger.transaction.domain.model.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialTransactionTest {

    private final UUID txId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID accountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID requestedBy = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID correlationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final Instant now = Instant.parse("2026-11-13T20:00:00Z");

    @Test
    @DisplayName("createDeposit initializes fields correctly with PENDING status and null sourceAccountId")
    void shouldCreateDepositTransaction() {
        FinancialTransaction tx = FinancialTransaction.createDeposit(
                txId,
                accountId,
                50000L,
                "Depósito em dinheiro",
                requestedBy,
                correlationId,
                now
        );

        assertThat(tx.getId()).isEqualTo(txId);
        assertThat(tx.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(tx.getSourceAccountId()).isNull();
        assertThat(tx.getDestinationAccountId()).isEqualTo(accountId);
        assertThat(tx.getAmount()).isEqualTo(50000L);
        assertThat(tx.getCurrency()).isEqualTo(BankingConstants.DEFAULT_CURRENCY);
        assertThat(tx.getDescription()).isEqualTo("Depósito em dinheiro");
        assertThat(tx.getRequestedBy()).isEqualTo(requestedBy);
        assertThat(tx.getCorrelationId()).isEqualTo(correlationId);
        assertThat(tx.getCreatedAt()).isEqualTo(now);
        assertThat(tx.getUpdatedAt()).isEqualTo(now);
        assertThat(tx.getCompletedAt()).isNull();
        assertThat(tx.getFailureCode()).isNull();
        assertThat(tx.getFailureReason()).isNull();
        assertThat(tx.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("createWithdrawal initializes fields correctly with PENDING status and null destinationAccountId")
    void shouldCreateWithdrawalTransaction() {
        FinancialTransaction tx = FinancialTransaction.createWithdrawal(
                txId,
                accountId,
                25000L,
                "Saque terminal ATM",
                requestedBy,
                correlationId,
                now
        );

        assertThat(tx.getId()).isEqualTo(txId);
        assertThat(tx.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(tx.getSourceAccountId()).isEqualTo(accountId);
        assertThat(tx.getDestinationAccountId()).isNull();
        assertThat(tx.getAmount()).isEqualTo(25000L);
        assertThat(tx.getCurrency()).isEqualTo(BankingConstants.DEFAULT_CURRENCY);
        assertThat(tx.getDescription()).isEqualTo("Saque terminal ATM");
        assertThat(tx.getRequestedBy()).isEqualTo(requestedBy);
        assertThat(tx.getCorrelationId()).isEqualTo(correlationId);
        assertThat(tx.getCreatedAt()).isEqualTo(now);
        assertThat(tx.getUpdatedAt()).isEqualTo(now);
        assertThat(tx.getCompletedAt()).isNull();
        assertThat(tx.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("createDeposit throws IllegalArgumentException when amount <= 0")
    void shouldThrowExceptionWhenDepositAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> FinancialTransaction.createDeposit(txId, accountId, 0L, "desc", requestedBy, correlationId, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be greater than zero");

        assertThatThrownBy(() -> FinancialTransaction.createDeposit(txId, accountId, -100L, "desc", requestedBy, correlationId, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be greater than zero");
    }

    @Test
    @DisplayName("createWithdrawal throws IllegalArgumentException when amount <= 0")
    void shouldThrowExceptionWhenWithdrawalAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> FinancialTransaction.createWithdrawal(txId, accountId, 0L, "desc", requestedBy, correlationId, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be greater than zero");
    }

    @Test
    @DisplayName("createDeposit throws NullPointerException when required fields are missing")
    void shouldThrowWhenRequiredFieldsMissingInDeposit() {
        assertThatThrownBy(() -> FinancialTransaction.createDeposit(txId, null, 1000L, "desc", requestedBy, correlationId, now))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("destinationAccountId");

        assertThatThrownBy(() -> FinancialTransaction.createDeposit(txId, accountId, 1000L, "desc", null, correlationId, now))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestedBy");
    }

    @Test
    @DisplayName("complete transition sets status to COMPLETED and records completedAt and updatedAt")
    void shouldCompleteTransaction() {
        FinancialTransaction tx = FinancialTransaction.createDeposit(
                txId, accountId, 1000L, "desc", requestedBy, correlationId, now
        );

        Instant completionTime = now.plusSeconds(5);
        tx.complete(completionTime);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getCompletedAt()).isEqualTo(completionTime);
        assertThat(tx.getUpdatedAt()).isEqualTo(completionTime);
    }

    @Test
    @DisplayName("fail transition sets status to FAILED and records failure details")
    void shouldFailTransaction() {
        FinancialTransaction tx = FinancialTransaction.createWithdrawal(
                txId, accountId, 1000L, "desc", requestedBy, correlationId, now
        );

        Instant failureTime = now.plusSeconds(3);
        tx.fail(BankingErrorCodes.INSUFFICIENT_FUNDS, "Saldo insuficiente para saque", failureTime);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getFailureCode()).isEqualTo(BankingErrorCodes.INSUFFICIENT_FUNDS);
        assertThat(tx.getFailureReason()).isEqualTo("Saldo insuficiente para saque");
        assertThat(tx.getCompletedAt()).isEqualTo(failureTime);
        assertThat(tx.getUpdatedAt()).isEqualTo(failureTime);
    }

    @Test
    @DisplayName("attempting to fail a completed transaction throws IllegalStateException")
    void shouldThrowWhenFailingCompletedTransaction() {
        FinancialTransaction tx = FinancialTransaction.createDeposit(
                txId, accountId, 1000L, "desc", requestedBy, correlationId, now
        );
        tx.complete(now.plusSeconds(1));

        assertThatThrownBy(() -> tx.fail("ERR", "Reason", now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal transaction transition from COMPLETED to FAILED");
    }

    @Test
    @DisplayName("attempting to complete a failed transaction throws IllegalStateException")
    void shouldThrowWhenCompletingFailedTransaction() {
        FinancialTransaction tx = FinancialTransaction.createWithdrawal(
                txId, accountId, 1000L, "desc", requestedBy, correlationId, now
        );
        tx.fail(BankingErrorCodes.INSUFFICIENT_FUNDS, "Saldo insuficiente", now.plusSeconds(1));

        assertThatThrownBy(() -> tx.complete(now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal transaction transition from FAILED to COMPLETED");
    }

    @Test
    @DisplayName("attempting to complete an already completed transaction throws IllegalStateException")
    void shouldThrowWhenCompletingAlreadyCompletedTransaction() {
        FinancialTransaction tx = FinancialTransaction.createDeposit(
                txId, accountId, 1000L, "desc", requestedBy, correlationId, now
        );
        tx.complete(now.plusSeconds(1));

        assertThatThrownBy(() -> tx.complete(now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal transaction transition from COMPLETED to COMPLETED");
    }

    @Test
    @DisplayName("attempting to fail an already failed transaction throws IllegalStateException")
    void shouldThrowWhenFailingAlreadyFailedTransaction() {
        FinancialTransaction tx = FinancialTransaction.createWithdrawal(
                txId, accountId, 1000L, "desc", requestedBy, correlationId, now
        );
        tx.fail("ERR_1", "Reason 1", now.plusSeconds(1));

        assertThatThrownBy(() -> tx.fail("ERR_2", "Reason 2", now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal transaction transition from FAILED to FAILED");
    }

    @Test
    @DisplayName("equals and hashCode contract based on ID")
    void shouldVerifyEqualsAndHashCode() {
        FinancialTransaction tx1 = FinancialTransaction.createDeposit(
                txId, accountId, 1000L, "desc", requestedBy, correlationId, now
        );
        FinancialTransaction tx2 = FinancialTransaction.createWithdrawal(
                txId, accountId, 2000L, "other desc", requestedBy, correlationId, now
        );
        FinancialTransaction tx3 = FinancialTransaction.createDeposit(
                UUID.randomUUID(), accountId, 1000L, "desc", requestedBy, correlationId, now
        );

        assertThat(tx1).isEqualTo(tx2);
        assertThat(tx1.hashCode()).isEqualTo(tx2.hashCode());
        assertThat(tx1).isNotEqualTo(tx3);
        assertThat(tx1).isNotEqualTo(null);
        assertThat(tx1.toString()).contains(txId.toString());
    }

    @Test
    @DisplayName("default factories handle null id and correlationId by auto-generating")
    void shouldHandleNullOptionalFieldsInFactories() {
        FinancialTransaction tx = FinancialTransaction.createDeposit(
                null, accountId, 1000L, "desc", requestedBy, null, null
        );

        assertThat(tx.getId()).isNotNull();
        assertThat(tx.getCorrelationId()).isNotNull();
        assertThat(tx.getCreatedAt()).isNotNull();
    }
}
