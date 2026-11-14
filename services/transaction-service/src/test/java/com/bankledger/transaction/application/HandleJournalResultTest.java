package com.bankledger.transaction.application;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.commons.constant.BankingErrorCodes;
import com.bankledger.transaction.application.port.in.HandleJournalResultUseCase;
import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.application.service.HandleJournalResultService;
import com.bankledger.transaction.domain.event.JournalResultPayload;
import com.bankledger.transaction.domain.exception.TransactionNotFoundException;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import com.bankledger.transaction.domain.model.TransactionStatus;
import com.bankledger.transaction.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HandleJournalResultService Unit Tests")
class HandleJournalResultTest {

    @Mock
    private TransactionRepositoryPort transactionRepositoryPort;

    private Clock clock;
    private Instant fixedInstant;
    private HandleJournalResultUseCase service;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-11-13T20:00:00Z");
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        service = new HandleJournalResultService(transactionRepositoryPort, clock);
    }

    @Test
    @DisplayName("Should transition transaction to COMPLETED when journal result is POSTED")
    void shouldTransitionToCompletedWhenStatusIsPosted() {
        UUID txId = UUID.randomUUID();
        UUID destAccountId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        Instant createdAt = fixedInstant.minusSeconds(60);

        FinancialTransaction existingTx = new FinancialTransaction(
                txId,
                TransactionType.DEPOSIT,
                TransactionStatus.PENDING,
                null,
                destAccountId,
                50000L,
                "BRL",
                "Depósito teste",
                requestedBy,
                null,
                null,
                correlationId,
                createdAt,
                createdAt,
                null,
                0L
        );

        when(transactionRepositoryPort.findById(txId)).thenReturn(Optional.of(existingTx));
        when(transactionRepositoryPort.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        JournalResultPayload payload = new JournalResultPayload(
                journalId,
                txId,
                "DEPOSIT",
                "POSTED",
                50000L,
                "BRL",
                null,
                null,
                correlationId,
                fixedInstant
        );

        service.handleResult(payload);

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepositoryPort).save(captor.capture());

        FinancialTransaction saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(txId);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(saved.getCompletedAt()).isEqualTo(fixedInstant);
        assertThat(saved.getUpdatedAt()).isEqualTo(fixedInstant);
        assertThat(saved.getFailureCode()).isNull();
        assertThat(saved.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("Should transition transaction to FAILED when journal result is REJECTED")
    void shouldTransitionToFailedWhenStatusIsRejected() {
        UUID txId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant createdAt = fixedInstant.minusSeconds(60);

        FinancialTransaction existingTx = new FinancialTransaction(
                txId,
                TransactionType.WITHDRAWAL,
                TransactionStatus.PENDING,
                sourceAccountId,
                null,
                100000L,
                "BRL",
                "Saque teste",
                requestedBy,
                null,
                null,
                correlationId,
                createdAt,
                createdAt,
                null,
                0L
        );

        when(transactionRepositoryPort.findById(txId)).thenReturn(Optional.of(existingTx));
        when(transactionRepositoryPort.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        JournalResultPayload payload = new JournalResultPayload(
                null,
                txId,
                "WITHDRAWAL",
                "REJECTED",
                100000L,
                BankingConstants.DEFAULT_CURRENCY,
                BankingErrorCodes.INSUFFICIENT_FUNDS,
                "Saldo insuficiente para saque",
                correlationId,
                fixedInstant
        );

        service.handleResult(payload);

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepositoryPort).save(captor.capture());

        FinancialTransaction saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(txId);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(BankingErrorCodes.INSUFFICIENT_FUNDS);
        assertThat(saved.getFailureReason()).isEqualTo("Saldo insuficiente para saque");
        assertThat(saved.getCompletedAt()).isEqualTo(fixedInstant);
        assertThat(saved.getUpdatedAt()).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("Should skip processing idempotently when transaction is already resolved (COMPLETED)")
    void shouldSkipWhenTransactionAlreadyCompleted() {
        UUID txId = UUID.randomUUID();
        UUID destAccountId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant createdAt = fixedInstant.minusSeconds(120);
        Instant completedAt = fixedInstant.minusSeconds(60);

        FinancialTransaction resolvedTx = new FinancialTransaction(
                txId,
                TransactionType.DEPOSIT,
                TransactionStatus.COMPLETED,
                null,
                destAccountId,
                50000L,
                "BRL",
                "Depósito teste",
                requestedBy,
                null,
                null,
                correlationId,
                createdAt,
                completedAt,
                completedAt,
                1L
        );

        when(transactionRepositoryPort.findById(txId)).thenReturn(Optional.of(resolvedTx));

        JournalResultPayload payload = new JournalResultPayload(
                UUID.randomUUID(),
                txId,
                "DEPOSIT",
                "POSTED",
                50000L,
                "BRL",
                null,
                null,
                correlationId,
                fixedInstant
        );

        service.handleResult(payload);

        verify(transactionRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Should skip processing idempotently when transaction is already resolved (FAILED)")
    void shouldSkipWhenTransactionAlreadyFailed() {
        UUID txId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant createdAt = fixedInstant.minusSeconds(120);
        Instant completedAt = fixedInstant.minusSeconds(60);

        FinancialTransaction resolvedTx = new FinancialTransaction(
                txId,
                TransactionType.WITHDRAWAL,
                TransactionStatus.FAILED,
                sourceAccountId,
                null,
                50000L,
                BankingConstants.DEFAULT_CURRENCY,
                "Saque teste",
                requestedBy,
                BankingErrorCodes.INSUFFICIENT_FUNDS,
                "Saldo insuficiente",
                correlationId,
                createdAt,
                completedAt,
                completedAt,
                1L
        );

        when(transactionRepositoryPort.findById(txId)).thenReturn(Optional.of(resolvedTx));

        JournalResultPayload payload = new JournalResultPayload(
                null,
                txId,
                "WITHDRAWAL",
                "REJECTED",
                50000L,
                BankingConstants.DEFAULT_CURRENCY,
                BankingErrorCodes.INSUFFICIENT_FUNDS,
                "Saldo insuficiente",
                correlationId,
                fixedInstant
        );

        service.handleResult(payload);

        verify(transactionRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Should throw TransactionNotFoundException when transaction does not exist")
    void shouldThrowWhenTransactionNotFound() {
        UUID txId = UUID.randomUUID();
        when(transactionRepositoryPort.findById(txId)).thenReturn(Optional.empty());

        JournalResultPayload payload = new JournalResultPayload(
                UUID.randomUUID(),
                txId,
                "DEPOSIT",
                "POSTED",
                50000L,
                "BRL",
                null,
                null,
                UUID.randomUUID(),
                fixedInstant
        );

        assertThatThrownBy(() -> service.handleResult(payload))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(txId.toString());

        verify(transactionRepositoryPort, never()).save(any());
    }
}
