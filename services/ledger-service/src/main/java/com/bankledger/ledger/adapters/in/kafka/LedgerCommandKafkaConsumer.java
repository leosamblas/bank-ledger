package com.bankledger.ledger.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.ledger.PostJournalCommandPayload;
import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.ledger.application.port.in.PostJournalUseCase;
import com.bankledger.ledger.domain.command.PostJournalCommand;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound Kafka consumer adapter for double-entry ledger commands.
 * Enforces exactly-once execution via {@link InboxHandler} and commits offsets
 * manually via {@link Acknowledgment#acknowledge()} only upon successful execution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerCommandKafkaConsumer {

    public static final String HANDLER_NAME = "LedgerCommandHandler";

    private final PostJournalUseCase postJournalUseCase;
    private final InboxHandler inboxHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.commands:" + KafkaTopics.LEDGER_COMMANDS_V1 + "}",
            groupId = "${spring.kafka.consumer.group-id:ledger-service-commands}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record must not be null");
        Objects.requireNonNull(acknowledgment, "acknowledgment must not be null");

        log.info("Received Ledger command message: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            ParsedRecord parsed = parseRecord(record);

            inboxHandler.execute(parsed.eventId(), HANDLER_NAME, () -> {
                postJournalUseCase.postJournal(parsed.command());
            });

            acknowledgment.acknowledge();
            log.info("Acknowledged Kafka command record for eventId={}, transactionId={}",
                    parsed.eventId(), parsed.command().transactionId());
        } catch (Exception ex) {
            log.error("Failed to process Ledger command Kafka record: topic={}, offset={}, key={}, error={}",
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

        PostJournalCommandPayload payload;
        try {
            payload = objectMapper.treeToValue(dataNode, PostJournalCommandPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize PostJournalCommandPayload: " + e.getMessage(), e);
        }

        if (payload == null) {
            throw new IllegalArgumentException("PostJournalCommandPayload must not be null");
        }

        PostJournalCommand command = PostJournalCommand.fromPayload(payload);

        if (eventId == null) {
            if (dataNode.has("commandId") && !dataNode.get("commandId").isNull() && !dataNode.get("commandId").asString().isBlank()) {
                eventId = parseUuid(dataNode.get("commandId").asString());
            } else if (command.transactionId() != null) {
                eventId = command.transactionId();
            } else if (record.key() != null) {
                eventId = parseUuid(record.key());
            }
        }

        if (eventId == null) {
            eventId = UUID.randomUUID();
        }

        return new ParsedRecord(eventId, command);
    }

    private Optional<UUID> extractHeaderUuid(ConsumerRecord<String, String> record, String headerName) {
        if (record.headers() == null) {
            return Optional.empty();
        }
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            try {
                return Optional.of(UUID.fromString(new String(header.value(), StandardCharsets.UTF_8)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    private UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record ParsedRecord(UUID eventId, PostJournalCommand command) {}
}
