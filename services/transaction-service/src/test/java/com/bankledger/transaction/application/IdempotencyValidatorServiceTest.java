package com.bankledger.transaction.application;

import com.bankledger.transaction.application.port.in.IdempotencyCheckResult;
import com.bankledger.transaction.application.port.out.IdempotencyRepositoryPort;
import com.bankledger.transaction.application.service.IdempotencyValidatorService;
import com.bankledger.transaction.domain.exception.IdempotencyConflictException;
import com.bankledger.transaction.domain.exception.IdempotencyKeyReusedException;
import com.bankledger.transaction.domain.model.IdempotencyRecord;
import com.bankledger.transaction.domain.model.IdempotencyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyValidatorServiceTest {

    @Mock
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    private Clock clock;
    private Instant fixedInstant;
    private IdempotencyValidatorService service;

    private final UUID scope = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID key = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final String endpoint = "/api/v1/transactions/deposits";
    private final String requestHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-11-13T20:00:00Z");
        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        service = new IdempotencyValidatorService(idempotencyRepositoryPort, clock);
    }

    @Nested
    @DisplayName("validateOrLock")
    class ValidateOrLockTests {

        @Test
        @DisplayName("Case 1: Chave inédita: cria registro com status IN_PROGRESS e expiração em 24h")
        void shouldCreateInProgressRecordWhenKeyDoesNotExist() {
            when(idempotencyRepositoryPort.findByScopeAndKey(scope, key)).thenReturn(Optional.empty());
            when(idempotencyRepositoryPort.save(any(IdempotencyRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            IdempotencyCheckResult result = service.validateOrLock(scope, key, endpoint, requestHash, fixedInstant);

            assertThat(result.isProceed()).isTrue();
            assertThat(result.isCached()).isFalse();
            assertThat(result.responseStatus()).isNull();
            assertThat(result.responseBody()).isNull();
            assertThat(result.transactionId()).isNull();

            ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
            verify(idempotencyRepositoryPort).save(captor.capture());

            IdempotencyRecord saved = captor.getValue();
            assertThat(saved.scope()).isEqualTo(scope);
            assertThat(saved.key()).isEqualTo(key);
            assertThat(saved.endpoint()).isEqualTo(endpoint);
            assertThat(saved.requestHash()).isEqualTo(requestHash);
            assertThat(saved.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
            assertThat(saved.createdAt()).isEqualTo(fixedInstant);
            assertThat(saved.expiresAt()).isEqualTo(fixedInstant.plus(Duration.ofHours(24)));
            assertThat(saved.transactionId()).isNull();
            assertThat(saved.responseStatus()).isNull();
            assertThat(saved.responseBody()).isNull();
        }

        @Test
        @DisplayName("Case 1b: validateOrLock sem Instant explícito utiliza o Clock injetado")
        void shouldCreateInProgressRecordUsingInjectedClock() {
            when(idempotencyRepositoryPort.findByScopeAndKey(scope, key)).thenReturn(Optional.empty());
            when(idempotencyRepositoryPort.save(any(IdempotencyRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            IdempotencyCheckResult result = service.validateOrLock(scope, key, endpoint, requestHash);

            assertThat(result.isProceed()).isTrue();

            ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
            verify(idempotencyRepositoryPort).save(captor.capture());
            assertThat(captor.getValue().createdAt()).isEqualTo(fixedInstant);
            assertThat(captor.getValue().expiresAt()).isEqualTo(fixedInstant.plus(Duration.ofHours(24)));
        }

        @Test
        @DisplayName("Case 2: Chave com status IN_PROGRESS: lança IdempotencyConflictException (HTTP 409)")
        void shouldThrowConflictExceptionWhenRecordIsInProgress() {
            IdempotencyRecord existing = new IdempotencyRecord(
                    scope,
                    key,
                    endpoint,
                    requestHash,
                    IdempotencyStatus.IN_PROGRESS,
                    null,
                    null,
                    null,
                    fixedInstant.minusSeconds(10),
                    fixedInstant.plus(Duration.ofHours(24))
            );
            when(idempotencyRepositoryPort.findByScopeAndKey(scope, key)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.validateOrLock(scope, key, endpoint, requestHash, fixedInstant))
                    .isInstanceOf(IdempotencyConflictException.class)
                    .hasMessage("Operação em processamento com a chave de idempotência informada.");

            verify(idempotencyRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("Case 3: Chave com status COMPLETED e mesmo hash de payload: retorna o resultado em cache para replay idêntico")
        void shouldReturnCachedResultWhenRecordIsCompletedWithSameHash() {
            UUID txId = UUID.randomUUID();
            String responseBody = "{\"id\":\"" + txId + "\",\"status\":\"PENDING\"}";
            int responseStatus = 202;

            IdempotencyRecord existing = new IdempotencyRecord(
                    scope,
                    key,
                    endpoint,
                    requestHash,
                    IdempotencyStatus.COMPLETED,
                    txId,
                    responseStatus,
                    responseBody,
                    fixedInstant.minusSeconds(30),
                    fixedInstant.plus(Duration.ofHours(24))
            );
            when(idempotencyRepositoryPort.findByScopeAndKey(scope, key)).thenReturn(Optional.of(existing));

            IdempotencyCheckResult result = service.validateOrLock(scope, key, endpoint, requestHash, fixedInstant);

            assertThat(result.isProceed()).isFalse();
            assertThat(result.isCached()).isTrue();
            assertThat(result.responseStatus()).isEqualTo(responseStatus);
            assertThat(result.responseBody()).isEqualTo(responseBody);
            assertThat(result.transactionId()).isEqualTo(txId);

            verify(idempotencyRepositoryPort, never()).save(any());
        }

        @Test
        @DisplayName("Case 4: Chave com status COMPLETED e hash diferente: lança IdempotencyKeyReusedException (HTTP 422)")
        void shouldThrowKeyReusedExceptionWhenRecordIsCompletedWithDifferentHash() {
            UUID txId = UUID.randomUUID();
            String existingHash = "aaaaa44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            String differentHash = "bbbbb44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

            IdempotencyRecord existing = new IdempotencyRecord(
                    scope,
                    key,
                    endpoint,
                    existingHash,
                    IdempotencyStatus.COMPLETED,
                    txId,
                    202,
                    "{\"id\":\"" + txId + "\"}",
                    fixedInstant.minusSeconds(60),
                    fixedInstant.plus(Duration.ofHours(24))
            );
            when(idempotencyRepositoryPort.findByScopeAndKey(scope, key)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.validateOrLock(scope, key, endpoint, differentHash, fixedInstant))
                    .isInstanceOf(IdempotencyKeyReusedException.class)
                    .hasMessage("Chave de idempotência já utilizada com um payload diferente.");

            verify(idempotencyRepositoryPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("complete")
    class CompleteTests {

        @Test
        @DisplayName("Deve atualizar o registro para COMPLETED com status HTTP, corpo e ID da transação")
        void shouldCompleteExistingRecord() {
            UUID txId = UUID.randomUUID();
            String responseBody = "{\"id\":\"" + txId + "\",\"status\":\"PENDING\"}";
            int responseStatus = 202;

            IdempotencyRecord inProgressRecord = new IdempotencyRecord(
                    scope,
                    key,
                    endpoint,
                    requestHash,
                    IdempotencyStatus.IN_PROGRESS,
                    null,
                    null,
                    null,
                    fixedInstant.minusSeconds(5),
                    fixedInstant.plus(Duration.ofHours(24))
            );
            when(idempotencyRepositoryPort.findByScopeAndKey(scope, key)).thenReturn(Optional.of(inProgressRecord));
            when(idempotencyRepositoryPort.save(any(IdempotencyRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.complete(scope, key, txId, responseStatus, responseBody, fixedInstant);

            ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
            verify(idempotencyRepositoryPort).save(captor.capture());

            IdempotencyRecord saved = captor.getValue();
            assertThat(saved.scope()).isEqualTo(scope);
            assertThat(saved.key()).isEqualTo(key);
            assertThat(saved.status()).isEqualTo(IdempotencyStatus.COMPLETED);
            assertThat(saved.transactionId()).isEqualTo(txId);
            assertThat(saved.responseStatus()).isEqualTo(responseStatus);
            assertThat(saved.responseBody()).isEqualTo(responseBody);
            assertThat(saved.endpoint()).isEqualTo(endpoint);
            assertThat(saved.requestHash()).isEqualTo(requestHash);
        }

        @Test
        @DisplayName("Deve lançar IllegalStateException quando tentar completar chave inexistente")
        void shouldThrowWhenRecordNotFoundOnComplete() {
            UUID txId = UUID.randomUUID();
            when(idempotencyRepositoryPort.findByScopeAndKey(scope, key)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.complete(scope, key, txId, 202, "{}", fixedInstant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Idempotency record not found");
        }
    }

    @Nested
    @DisplayName("computeHash")
    class ComputeHashTests {

        @Test
        @DisplayName("Deve computar SHA-256 hex string de 64 caracteres")
        void shouldComputeCorrectSha256Hex() throws NoSuchAlgorithmException {
            String raw = "{\"amount\":1000,\"description\":\"Test\"}";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expectedBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expectedBytes);

            String hash = service.computeHash(raw);

            assertThat(hash).isEqualTo(expectedHex);
            assertThat(hash).hasSize(64);
        }

        @Test
        @DisplayName("Deve computar SHA-256 de string vazia quando rawBody for nulo")
        void shouldHandleNullBodyGracefully() throws NoSuchAlgorithmException {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expectedBytes = digest.digest("".getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expectedBytes);

            String hash = service.computeHash(null);

            assertThat(hash).isEqualTo(expectedHex);
        }
    }
}
