package com.bankledger.transaction.domain;

import com.bankledger.transaction.domain.model.IdempotencyRecord;
import com.bankledger.transaction.domain.model.IdempotencyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyRecordTest {

    private final UUID scope = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();
    private final String endpoint = "/api/v1/transactions/deposits";
    private final String hash = "abc123hash";
    private final Instant now = Instant.parse("2026-11-13T20:00:00Z");
    private final Instant expiresAt = now.plus(Duration.ofHours(24));

    @Test
    @DisplayName("Deve criar IdempotencyRecord em progresso através da factory inProgress")
    void shouldCreateInProgressRecord() {
        IdempotencyRecord record = IdempotencyRecord.inProgress(scope, key, endpoint, hash, now, expiresAt);

        assertThat(record.scope()).isEqualTo(scope);
        assertThat(record.key()).isEqualTo(key);
        assertThat(record.endpoint()).isEqualTo(endpoint);
        assertThat(record.requestHash()).isEqualTo(hash);
        assertThat(record.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(record.createdAt()).isEqualTo(now);
        assertThat(record.expiresAt()).isEqualTo(expiresAt);
        assertThat(record.transactionId()).isNull();
        assertThat(record.responseStatus()).isNull();
        assertThat(record.responseBody()).isNull();
    }

    @Test
    @DisplayName("Deve criar cópia completada através do método withCompleted")
    void shouldCreateCompletedRecord() {
        IdempotencyRecord inProgress = IdempotencyRecord.inProgress(scope, key, endpoint, hash, now, expiresAt);
        UUID txId = UUID.randomUUID();
        int status = 202;
        String body = "{\"status\":\"PENDING\"}";

        IdempotencyRecord completed = inProgress.withCompleted(txId, status, body);

        assertThat(completed.scope()).isEqualTo(scope);
        assertThat(completed.key()).isEqualTo(key);
        assertThat(completed.endpoint()).isEqualTo(endpoint);
        assertThat(completed.requestHash()).isEqualTo(hash);
        assertThat(completed.status()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(completed.transactionId()).isEqualTo(txId);
        assertThat(completed.responseStatus()).isEqualTo(status);
        assertThat(completed.responseBody()).isEqualTo(body);
        assertThat(completed.createdAt()).isEqualTo(now);
        assertThat(completed.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("Deve lançar NullPointerException se atributos obrigatórios forem nulos")
    void shouldRejectNullMandatoryAttributes() {
        assertThatThrownBy(() -> new IdempotencyRecord(null, key, endpoint, hash, IdempotencyStatus.IN_PROGRESS, null, null, null, now, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("scope must not be null");

        assertThatThrownBy(() -> new IdempotencyRecord(scope, null, endpoint, hash, IdempotencyStatus.IN_PROGRESS, null, null, null, now, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("key must not be null");

        assertThatThrownBy(() -> new IdempotencyRecord(scope, key, null, hash, IdempotencyStatus.IN_PROGRESS, null, null, null, now, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("endpoint must not be null");

        assertThatThrownBy(() -> new IdempotencyRecord(scope, key, endpoint, null, IdempotencyStatus.IN_PROGRESS, null, null, null, now, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestHash must not be null");

        assertThatThrownBy(() -> new IdempotencyRecord(scope, key, endpoint, hash, null, null, null, null, now, expiresAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status must not be null");
    }
}
