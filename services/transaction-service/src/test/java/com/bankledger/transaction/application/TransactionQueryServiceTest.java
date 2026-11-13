package com.bankledger.transaction.application;

import com.bankledger.transaction.application.port.out.TransactionRepositoryPort;
import com.bankledger.transaction.application.service.TransactionQueryService;
import com.bankledger.transaction.domain.exception.TransactionNotFoundException;
import com.bankledger.transaction.domain.model.FinancialTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceTest {

    @Mock
    private TransactionRepositoryPort transactionRepositoryPort;

    private TransactionQueryService service;

    private final UUID transactionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID authorizedUser = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID attackerUser = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private final UUID accountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final Instant now = Instant.parse("2026-11-13T20:30:00Z");

    @BeforeEach
    void setUp() {
        service = new TransactionQueryService(transactionRepositoryPort);
    }

    @Test
    @DisplayName("getTransaction returns transaction when id exists and requestedBy matches")
    void shouldReturnTransactionWhenAuthorized() {
        FinancialTransaction tx = FinancialTransaction.createDeposit(
                transactionId,
                accountId,
                10000L,
                "Depósito teste",
                authorizedUser,
                UUID.randomUUID(),
                now
        );
        when(transactionRepositoryPort.findById(transactionId)).thenReturn(Optional.of(tx));

        FinancialTransaction result = service.getTransaction(transactionId, authorizedUser);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(transactionId);
        assertThat(result.getRequestedBy()).isEqualTo(authorizedUser);
    }

    @Test
    @DisplayName("getTransaction throws TransactionNotFoundException when transaction does not exist")
    void shouldThrowNotFoundWhenTransactionDoesNotExist() {
        when(transactionRepositoryPort.findById(transactionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransaction(transactionId, authorizedUser))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    @DisplayName("OWASP A01 BOLA/IDOR: getTransaction throws TransactionNotFoundException when requestedBy differs from owner")
    void shouldThrowNotFoundWhenRequestedByDoesNotMatchOwner() {
        FinancialTransaction tx = FinancialTransaction.createDeposit(
                transactionId,
                accountId,
                10000L,
                "Depósito confidencial",
                authorizedUser,
                UUID.randomUUID(),
                now
        );
        when(transactionRepositoryPort.findById(transactionId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> service.getTransaction(transactionId, attackerUser))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    @DisplayName("getTransaction throws NullPointerException when arguments are null")
    void shouldThrowWhenArgumentsAreNull() {
        assertThatThrownBy(() -> service.getTransaction(null, authorizedUser))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("transactionId must not be null");

        assertThatThrownBy(() -> service.getTransaction(transactionId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestedBy must not be null");
    }
}
