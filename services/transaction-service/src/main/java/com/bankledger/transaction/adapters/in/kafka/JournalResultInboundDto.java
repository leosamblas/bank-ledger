package com.bankledger.transaction.adapters.in.kafka;

import com.bankledger.transaction.domain.event.JournalResultPayload;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Strongly-typed inbound DTO for accounting journal results received from Kafka.
 * Supports both CloudEvents 1.0 envelope payloads and flat payloads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JournalResultInboundDto(
        UUID journalId,
        UUID transactionId,
        String type,
        String status,
        Long totalAmount,
        String currency,
        String rejectCode,
        String rejectReason,
        UUID correlationId,
        Instant timestamp,
        Instant postedAt,
        Instant rejectedAt
) implements Serializable {

    /**
     * Converts inbound DTO to domain {@link JournalResultPayload}.
     *
     * @param envelopeType the event type from the envelope (e.g. "JournalPosted", "JournalRejected")
     * @param recordKey the Kafka record key (used as fallback transactionId)
     * @return strongly-typed domain {@link JournalResultPayload}
     */
    public JournalResultPayload toPayload(String envelopeType, String recordKey) {
        UUID resolvedTransactionId = this.transactionId != null
                ? this.transactionId
                : parseUuid(recordKey);

        String resolvedStatus = this.status;
        if (resolvedStatus == null || resolvedStatus.isBlank()) {
            String candidate = (envelopeType != null && !envelopeType.isBlank()) ? envelopeType : this.type;
            if (candidate != null) {
                if (candidate.contains("Posted")) {
                    resolvedStatus = "POSTED";
                } else if (candidate.contains("Rejected")) {
                    resolvedStatus = "REJECTED";
                }
            }
            if (resolvedStatus == null || resolvedStatus.isBlank()) {
                if (this.postedAt != null) {
                    resolvedStatus = "POSTED";
                } else if (this.rejectedAt != null || this.rejectCode != null) {
                    resolvedStatus = "REJECTED";
                }
            }
        }

        Instant effectiveTimestamp = this.timestamp != null
                ? this.timestamp
                : (this.postedAt != null ? this.postedAt : this.rejectedAt);

        return new JournalResultPayload(
                this.journalId,
                resolvedTransactionId,
                this.type,
                resolvedStatus,
                this.totalAmount,
                this.currency,
                this.rejectCode,
                this.rejectReason,
                this.correlationId,
                effectiveTimestamp
        );
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
