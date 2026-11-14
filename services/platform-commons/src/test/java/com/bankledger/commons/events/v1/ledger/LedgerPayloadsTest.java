package com.bankledger.commons.events.v1.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Ledger Payloads Canonical Records Tests")
class LedgerPayloadsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
    }

    @Test
    @DisplayName("PostJournalCommandPayload: should construct, apply defaults, and validate invariants")
    void shouldHandlePostJournalCommandPayload() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();

        // With null defaults: commandId, currency, correlationId, timestamp are generated
        PostJournalCommandPayload payload = new PostJournalCommandPayload(
                null,
                txId,
                "TRANSFER",
                5000L,
                null,
                srcId,
                dstId,
                "TERM-01",
                null,
                null
        );

        assertThat(payload.commandId()).isNotNull();
        assertThat(payload.transactionId()).isEqualTo(txId);
        assertThat(payload.type()).isEqualTo("TRANSFER");
        assertThat(payload.amount()).isEqualTo(5000L);
        assertThat(payload.currency()).isEqualTo("BRL");
        assertThat(payload.sourceAccountId()).isEqualTo(srcId);
        assertThat(payload.destinationAccountId()).isEqualTo(dstId);
        assertThat(payload.terminalId()).isEqualTo("TERM-01");
        assertThat(payload.correlationId()).isNotNull();
        assertThat(payload.timestamp()).isNotNull();

        // JSON serialization
        String json = objectMapper.writeValueAsString(payload);
        PostJournalCommandPayload deserialized = objectMapper.readValue(json, PostJournalCommandPayload.class);
        assertThat(deserialized).isEqualTo(payload);

        // Java Serializable
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(payload);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(ois.readObject()).isEqualTo(payload);
        }

        // Invariant validations
        assertThatThrownBy(() -> new PostJournalCommandPayload(UUID.randomUUID(), null, "TRANSFER", 100L, "BRL", null, null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("transactionId");

        assertThatThrownBy(() -> new PostJournalCommandPayload(UUID.randomUUID(), txId, null, 100L, "BRL", null, null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");

        assertThatThrownBy(() -> new PostJournalCommandPayload(UUID.randomUUID(), txId, "TRANSFER", 0L, "BRL", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");

        assertThatThrownBy(() -> new PostJournalCommandPayload(UUID.randomUUID(), txId, "TRANSFER", -10L, "BRL", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");
    }

    @Test
    @DisplayName("JournalPostedPayload: should construct, validate invariants, and serialize")
    void shouldHandleJournalPostedPayload() throws Exception {
        UUID journalId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant postedAt = Instant.now();

        JournalPostedPayload payload = new JournalPostedPayload(
                journalId,
                txId,
                "TRANSFER",
                "POSTED",
                10000L,
                "BRL",
                correlationId,
                postedAt
        );

        assertThat(payload.journalId()).isEqualTo(journalId);
        assertThat(payload.transactionId()).isEqualTo(txId);
        assertThat(payload.type()).isEqualTo("TRANSFER");
        assertThat(payload.status()).isEqualTo("POSTED");
        assertThat(payload.totalAmount()).isEqualTo(10000L);
        assertThat(payload.currency()).isEqualTo("BRL");
        assertThat(payload.correlationId()).isEqualTo(correlationId);
        assertThat(payload.postedAt()).isEqualTo(postedAt);

        // JSON serialization
        String json = objectMapper.writeValueAsString(payload);
        JournalPostedPayload deserialized = objectMapper.readValue(json, JournalPostedPayload.class);
        assertThat(deserialized).isEqualTo(payload);

        // Invariants
        assertThatThrownBy(() -> new JournalPostedPayload(null, txId, "TRANSFER", "POSTED", 10L, "BRL", correlationId, postedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("journalId");

        assertThatThrownBy(() -> new JournalPostedPayload(journalId, null, "TRANSFER", "POSTED", 10L, "BRL", correlationId, postedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("transactionId");

        assertThatThrownBy(() -> new JournalPostedPayload(journalId, txId, null, "POSTED", 10L, "BRL", correlationId, postedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");

        assertThatThrownBy(() -> new JournalPostedPayload(journalId, txId, "TRANSFER", null, 10L, "BRL", correlationId, postedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("status");

        assertThatThrownBy(() -> new JournalPostedPayload(journalId, txId, "TRANSFER", "POSTED", -1L, "BRL", correlationId, postedAt))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("totalAmount");

        assertThatThrownBy(() -> new JournalPostedPayload(journalId, txId, "TRANSFER", "POSTED", 10L, "BRL", null, postedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("correlationId");

        assertThatThrownBy(() -> new JournalPostedPayload(journalId, txId, "TRANSFER", "POSTED", 10L, "BRL", correlationId, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("postedAt");
    }

    @Test
    @DisplayName("JournalRejectedPayload: should construct, validate invariants, and serialize")
    void shouldHandleJournalRejectedPayload() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant rejectedAt = Instant.now();

        JournalRejectedPayload payload = new JournalRejectedPayload(
                txId,
                "TRANSFER",
                "REJECTED",
                "INSUFFICIENT_FUNDS",
                "Balance is insufficient for withdrawal",
                correlationId,
                rejectedAt
        );

        assertThat(payload.transactionId()).isEqualTo(txId);
        assertThat(payload.type()).isEqualTo("TRANSFER");
        assertThat(payload.status()).isEqualTo("REJECTED");
        assertThat(payload.rejectCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(payload.rejectReason()).isEqualTo("Balance is insufficient for withdrawal");
        assertThat(payload.correlationId()).isEqualTo(correlationId);
        assertThat(payload.rejectedAt()).isEqualTo(rejectedAt);

        // JSON serialization
        String json = objectMapper.writeValueAsString(payload);
        JournalRejectedPayload deserialized = objectMapper.readValue(json, JournalRejectedPayload.class);
        assertThat(deserialized).isEqualTo(payload);

        // Invariants
        assertThatThrownBy(() -> new JournalRejectedPayload(null, "TRANSFER", "REJECTED", "CODE", "REASON", correlationId, rejectedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("transactionId");

        assertThatThrownBy(() -> new JournalRejectedPayload(txId, null, "REJECTED", "CODE", "REASON", correlationId, rejectedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");

        assertThatThrownBy(() -> new JournalRejectedPayload(txId, "TRANSFER", null, "CODE", "REASON", correlationId, rejectedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("status");

        assertThatThrownBy(() -> new JournalRejectedPayload(txId, "TRANSFER", "REJECTED", null, "REASON", correlationId, rejectedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("rejectCode");

        assertThatThrownBy(() -> new JournalRejectedPayload(txId, "TRANSFER", "REJECTED", "CODE", null, correlationId, rejectedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("rejectReason");

        assertThatThrownBy(() -> new JournalRejectedPayload(txId, "TRANSFER", "REJECTED", "CODE", "REASON", null, rejectedAt))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("correlationId");

        assertThatThrownBy(() -> new JournalRejectedPayload(txId, "TRANSFER", "REJECTED", "CODE", "REASON", correlationId, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("rejectedAt");
    }

    @Test
    @DisplayName("EntryPostedPayload: should construct, validate invariants, and serialize")
    void shouldHandleEntryPostedPayload() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        EntryPostedPayload payload = new EntryPostedPayload(
                entryId,
                journalId,
                accountId,
                "CREDIT",
                1500L,
                timestamp
        );

        assertThat(payload.entryId()).isEqualTo(entryId);
        assertThat(payload.journalId()).isEqualTo(journalId);
        assertThat(payload.accountId()).isEqualTo(accountId);
        assertThat(payload.entryType()).isEqualTo("CREDIT");
        assertThat(payload.amount()).isEqualTo(1500L);
        assertThat(payload.timestamp()).isEqualTo(timestamp);

        // JSON serialization
        String json = objectMapper.writeValueAsString(payload);
        EntryPostedPayload deserialized = objectMapper.readValue(json, EntryPostedPayload.class);
        assertThat(deserialized).isEqualTo(payload);

        // Invariants
        assertThatThrownBy(() -> new EntryPostedPayload(null, journalId, accountId, "CREDIT", 100L, timestamp))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("entryId");

        assertThatThrownBy(() -> new EntryPostedPayload(entryId, null, accountId, "CREDIT", 100L, timestamp))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("journalId");

        assertThatThrownBy(() -> new EntryPostedPayload(entryId, journalId, null, "CREDIT", 100L, timestamp))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("accountId");

        assertThatThrownBy(() -> new EntryPostedPayload(entryId, journalId, accountId, null, 100L, timestamp))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("entryType");

        assertThatThrownBy(() -> new EntryPostedPayload(entryId, journalId, accountId, "CREDIT", 0L, timestamp))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");

        assertThatThrownBy(() -> new EntryPostedPayload(entryId, journalId, accountId, "CREDIT", -5L, timestamp))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");

        assertThatThrownBy(() -> new EntryPostedPayload(entryId, journalId, accountId, "CREDIT", 100L, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("timestamp");
    }
}
