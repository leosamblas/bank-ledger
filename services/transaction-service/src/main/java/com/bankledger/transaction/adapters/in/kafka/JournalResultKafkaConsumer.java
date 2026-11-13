package com.bankledger.transaction.adapters.in.kafka;

import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.transaction.application.port.in.HandleJournalResultUseCase;
import com.bankledger.transaction.domain.event.JournalResultPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound Kafka consumer listening for accounting results on topic {@code ledger.journal-results.v1}.
 * Resolves financial transactions to COMPLETED or FAILED via {@link HandleJournalResultUseCase}.
 * Uses {@link InboxHandler} to provide exactly-once delivery and manually acknowledges offset
 * only after successful processing and persistence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JournalResultKafkaConsumer {

    public static final String HANDLER_NAME = "JournalResultConsumer";

    private final HandleJournalResultUseCase handleJournalResultUseCase;
    private final InboxHandler inboxHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.journal-results:ledger.journal-results.v1}",
            groupId = "${spring.kafka.consumer.group-id:transaction-service-journal-results}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record must not be null");
        Objects.requireNonNull(acknowledgment, "acknowledgment must not be null");

        log.info("Received journal result Kafka record: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            ParsedRecord parsed = parseRecord(record);

            inboxHandler.execute(parsed.eventId(), HANDLER_NAME, () -> {
                handleJournalResultUseCase.handleResult(parsed.payload());
            });

            acknowledgment.acknowledge();
            log.info("Acknowledged Kafka journal result record for eventId={}, transactionId={}, status={}",
                    parsed.eventId(), parsed.payload().transactionId(), parsed.payload().status());
        } catch (Exception ex) {
            log.error("Failed to process journal result record: topic={}, offset={}, key={}, error={}",
                    record.topic(), record.offset(), record.key(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private ParsedRecord parseRecord(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Kafka record value must not be empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Kafka record JSON: " + e.getMessage(), e);
        }

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Kafka record payload must be a JSON object");
        }

        UUID eventId = null;
        JsonNode dataNode;

        if (root.has("data") && root.get("data").isObject()) {
            dataNode = root.get("data");
            if (root.has("id") && !root.get("id").isNull()) {
                eventId = parseUuid(root.get("id").asString());
            }
        } else {
            dataNode = root;
            if (root.has("eventId") && !root.get("eventId").isNull()) {
                eventId = parseUuid(root.get("eventId").asString());
            } else if (root.has("id") && !root.get("id").isNull()) {
                eventId = parseUuid(root.get("id").asString());
            }
        }

        if (eventId == null && record.headers() != null) {
            eventId = extractHeaderUuid(record, "ce_id")
                    .or(() -> extractHeaderUuid(record, "eventId"))
                    .or(() -> extractHeaderUuid(record, "id"))
                    .orElse(null);
        }

        JournalResultPayload payload = extractPayload(dataNode, root, record);

        if (eventId == null) {
            if (payload.journalId() != null) {
                eventId = payload.journalId();
            } else if (payload.transactionId() != null) {
                eventId = payload.transactionId();
            } else if (record.key() != null) {
                eventId = parseUuid(record.key());
            }
        }

        if (eventId == null) {
            eventId = UUID.randomUUID();
        }

        return new ParsedRecord(eventId, payload);
    }

    private JournalResultPayload extractPayload(JsonNode dataNode, JsonNode rootNode, ConsumerRecord<String, String> record) {
        UUID journalId = getOptionalUuid(dataNode, "journalId");
        UUID transactionId = getOptionalUuid(dataNode, "transactionId");
        if (transactionId == null && record.key() != null) {
            transactionId = parseUuid(record.key());
        }
        if (transactionId == null) {
            throw new IllegalArgumentException("Missing required transactionId in journal result payload");
        }

        String type = getOptionalText(dataNode, "type");
        String status = getOptionalText(dataNode, "status");
        if (status == null) {
            String eventType = getOptionalText(rootNode, "type");
            if (eventType != null) {
                if (eventType.contains("Posted")) {
                    status = "POSTED";
                } else if (eventType.contains("Rejected")) {
                    status = "REJECTED";
                }
            }
        }
        if (status == null) {
            throw new IllegalArgumentException("Missing required status in journal result payload");
        }

        Long totalAmount = getOptionalLong(dataNode, "totalAmount");
        if (totalAmount == null) {
            totalAmount = getOptionalLong(dataNode, "amount");
        }

        String currency = getOptionalText(dataNode, "currency");
        String rejectCode = getOptionalText(dataNode, "rejectCode");
        String rejectReason = getOptionalText(dataNode, "rejectReason");
        UUID correlationId = getOptionalUuid(dataNode, "correlationId");

        Instant timestamp = getOptionalInstant(dataNode, "postedAt");
        if (timestamp == null) {
            timestamp = getOptionalInstant(dataNode, "rejectedAt");
        }
        if (timestamp == null) {
            timestamp = getOptionalInstant(dataNode, "timestamp");
        }
        if (timestamp == null) {
            timestamp = getOptionalInstant(rootNode, "time");
        }

        return new JournalResultPayload(
                journalId,
                transactionId,
                type,
                status,
                totalAmount,
                currency,
                rejectCode,
                rejectReason,
                correlationId,
                timestamp
        );
    }

    private Optional<UUID> extractHeaderUuid(ConsumerRecord<String, String> record, String headerKey) {
        if (record.headers() == null) {
            return Optional.empty();
        }
        Header header = record.headers().lastHeader(headerKey);
        if (header != null && header.value() != null) {
            String val = new String(header.value(), StandardCharsets.UTF_8).replace("\"", "");
            try {
                return Optional.of(UUID.fromString(val));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID getOptionalUuid(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull() && !node.get(fieldName).asString().isBlank()) {
            return UUID.fromString(node.get(fieldName).asString());
        }
        return null;
    }

    private String getOptionalText(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asString();
        }
        return null;
    }

    private Long getOptionalLong(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull() && node.get(fieldName).isNumber()) {
            return node.get(fieldName).asLong();
        }
        return null;
    }

    private Instant getOptionalInstant(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            JsonNode value = node.get(fieldName);
            if (value.isNumber()) {
                return Instant.ofEpochMilli(value.asLong());
            }
            return Instant.parse(value.asString());
        }
        return null;
    }

    private record ParsedRecord(UUID eventId, JournalResultPayload payload) {}
}
