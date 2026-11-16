package com.bankledger.transaction.adapters.in.kafka;

import com.bankledger.transaction.domain.event.JournalResultPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JournalResultInboundDto Unit Tests")
class JournalResultInboundDtoTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("Should deserialize JSON and map all explicit fields to JournalResultPayload")
    void shouldMapExplicitFields() throws Exception {
        UUID journalId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-11-13T10:15:30Z");

        String json = String.format("""
                {
                  "journalId": "%s",
                  "transactionId": "%s",
                  "type": "PAYMENT",
                  "status": "POSTED",
                  "totalAmount": 25000,
                  "currency": "BRL",
                  "rejectCode": null,
                  "rejectReason": null,
                  "correlationId": "%s",
                  "timestamp": "%s",
                  "extraUnknownField": "ignored"
                }
                """, journalId, transactionId, correlationId, timestamp);

        JournalResultInboundDto dto = objectMapper.readValue(json, JournalResultInboundDto.class);
        JournalResultPayload payload = dto.toPayload(null, null);

        assertThat(payload.journalId()).isEqualTo(journalId);
        assertThat(payload.transactionId()).isEqualTo(transactionId);
        assertThat(payload.type()).isEqualTo("PAYMENT");
        assertThat(payload.status()).isEqualTo("POSTED");
        assertThat(payload.totalAmount()).isEqualTo(25000L);
        assertThat(payload.currency()).isEqualTo("BRL");
        assertThat(payload.correlationId()).isEqualTo(correlationId);
        assertThat(payload.timestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("Should resolve fallback transactionId from recordKey when transactionId is null")
    void shouldResolveFallbackTransactionIdFromRecordKey() {
        UUID recordKeyUuid = UUID.randomUUID();
        JournalResultInboundDto dto = new JournalResultInboundDto(
                null, null, "TRANSFER", "POSTED", 100L, "BRL",
                null, null, UUID.randomUUID(), Instant.now(), null, null
        );

        JournalResultPayload payload = dto.toPayload(null, recordKeyUuid.toString());

        assertThat(payload.transactionId()).isEqualTo(recordKeyUuid);
    }

    @Test
    @DisplayName("Should throw NullPointerException if transactionId cannot be resolved")
    void shouldThrowWhenTransactionIdCannotBeResolved() {
        JournalResultInboundDto dto = new JournalResultInboundDto(
                null, null, "TRANSFER", "POSTED", 100L, "BRL",
                null, null, UUID.randomUUID(), Instant.now(), null, null
        );

        assertThatThrownBy(() -> dto.toPayload(null, "invalid-uuid"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("transactionId must not be null");
    }

    @Test
    @DisplayName("Should resolve fallback status from envelopeType containing Posted or Rejected")
    void shouldResolveFallbackStatusFromEnvelopeType() {
        UUID txId = UUID.randomUUID();
        JournalResultInboundDto dtoPosted = new JournalResultInboundDto(
                null, txId, "TRANSFER", null, 100L, "BRL",
                null, null, UUID.randomUUID(), null, Instant.now(), null
        );
        JournalResultPayload payloadPosted = dtoPosted.toPayload("JournalPosted", null);
        assertThat(payloadPosted.status()).isEqualTo("POSTED");

        JournalResultInboundDto dtoRejected = new JournalResultInboundDto(
                null, txId, "TRANSFER", "   ", 100L, "BRL",
                "ERR_01", "Rejected", UUID.randomUUID(), null, null, Instant.now()
        );
        JournalResultPayload payloadRejected = dtoRejected.toPayload("com.bankledger.JournalRejected", null);
        assertThat(payloadRejected.status()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Should resolve effective timestamp with precedence: timestamp > postedAt > rejectedAt")
    void shouldResolveEffectiveTimestamp() {
        UUID txId = UUID.randomUUID();
        Instant ts = Instant.parse("2026-11-13T12:00:00Z");
        Instant posted = Instant.parse("2026-11-13T12:01:00Z");
        Instant rejected = Instant.parse("2026-11-13T12:02:00Z");

        // Precedence 1: timestamp present
        JournalResultInboundDto dto1 = new JournalResultInboundDto(
                null, txId, "TYPE", "POSTED", null, null, null, null, null, ts, posted, rejected
        );
        assertThat(dto1.toPayload(null, null).timestamp()).isEqualTo(ts);

        // Precedence 2: timestamp null, postedAt present
        JournalResultInboundDto dto2 = new JournalResultInboundDto(
                null, txId, "TYPE", "POSTED", null, null, null, null, null, null, posted, rejected
        );
        assertThat(dto2.toPayload(null, null).timestamp()).isEqualTo(posted);

        // Precedence 3: timestamp and postedAt null, rejectedAt present
        JournalResultInboundDto dto3 = new JournalResultInboundDto(
                null, txId, "TYPE", "REJECTED", null, null, null, null, null, null, null, rejected
        );
        assertThat(dto3.toPayload(null, null).timestamp()).isEqualTo(rejected);
    }
}
