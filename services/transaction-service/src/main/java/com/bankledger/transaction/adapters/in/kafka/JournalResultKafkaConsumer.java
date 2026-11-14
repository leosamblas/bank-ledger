package com.bankledger.transaction.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.ledger.JournalPostedPayload;
import com.bankledger.commons.events.v1.ledger.JournalRejectedPayload;
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
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
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
            topics = "${app.kafka.topics.journal-results:" + KafkaTopics.LEDGER_JOURNAL_RESULTS_V1 + "}",
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

        JournalResultPayload payload = deserializePayload(dataNode, root, record);

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

    private JournalResultPayload deserializePayload(JsonNode dataNode, JsonNode rootNode, ConsumerRecord<String, String> record) {
        if (dataNode == null || !dataNode.isObject()) {
            throw new IllegalArgumentException("Invalid payload data: expected JSON object");
        }

        if (dataNode instanceof ObjectNode objectNode) {
            if (!objectNode.has("transactionId") && record.key() != null) {
                objectNode.put("transactionId", record.key());
            }
            if (!objectNode.has("status") && rootNode != null && rootNode.has("type") && !rootNode.get("type").isNull()) {
                String eventType = rootNode.get("type").asString();
                if (eventType.contains("Posted")) {
                    objectNode.put("status", "POSTED");
                } else if (eventType.contains("Rejected")) {
                    objectNode.put("status", "REJECTED");
                }
            }
        }

        // 1. Try canonical JournalPostedPayload if postedAt is present
        if (dataNode.has("postedAt")) {
            try {
                JournalPostedPayload posted = objectMapper.treeToValue(dataNode, JournalPostedPayload.class);
                return JournalResultPayload.fromPosted(posted);
            } catch (Exception ignored) {
                // Fallback to generic JournalResultPayload
            }
        }

        // 2. Try canonical JournalRejectedPayload if rejectedAt or rejectCode is present
        if (dataNode.has("rejectedAt") || dataNode.has("rejectCode")) {
            try {
                JournalRejectedPayload rejected = objectMapper.treeToValue(dataNode, JournalRejectedPayload.class);
                return JournalResultPayload.fromRejected(rejected);
            } catch (Exception ignored) {
                // Fallback to generic JournalResultPayload
            }
        }

        // 3. Fallback / direct JournalResultPayload
        try {
            JournalResultPayload result = objectMapper.treeToValue(dataNode, JournalResultPayload.class);
            if (result == null) {
                throw new IllegalArgumentException("Deserialized JournalResultPayload is null");
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize journal result payload: " + e.getMessage(), e);
        }
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

    private record ParsedRecord(UUID eventId, JournalResultPayload payload) {}
}
