package com.bankledger.ledger.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.ledger.application.port.in.ProvisionAccountUseCase;
import com.bankledger.ledger.domain.event.AccountOpenedPayload;
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
 * Inbound Kafka consumer adapter for the AccountOpened event.
 * Enforces exactly-once execution via {@link InboxHandler} and commits offsets
 * manually via {@link Acknowledgment#acknowledge()} only upon successful processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountOpenedKafkaConsumer {

    public static final String HANDLER_NAME = "AccountOpenedConsumer";

    private final ProvisionAccountUseCase provisionAccountUseCase;
    private final InboxHandler inboxHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.account-events:" + KafkaTopics.ACCOUNT_EVENTS_V1 + "}",
            groupId = "${spring.kafka.consumer.group-id:ledger-service-account-opened}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record must not be null");
        Objects.requireNonNull(acknowledgment, "acknowledgment must not be null");

        log.info("Received AccountOpened message: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            ParsedRecord parsed = parseRecord(record);

            inboxHandler.execute(parsed.eventId(), HANDLER_NAME, () -> {
                provisionAccountUseCase.provisionAccount(parsed.payload());
            });

            acknowledgment.acknowledge();
            log.info("Acknowledged Kafka record for eventId={}, accountId={}",
                    parsed.eventId(), parsed.payload().accountId());
        } catch (Exception ex) {
            log.error("Failed to process AccountOpened Kafka record: topic={}, offset={}, key={}, error={}",
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

        AccountOpenedPayload payload = extractPayload(dataNode);

        if (eventId == null) {
            if (payload.accountId() != null) {
                eventId = payload.accountId();
            } else if (record.key() != null) {
                eventId = parseUuid(record.key());
            }
        }

        if (eventId == null) {
            eventId = UUID.randomUUID();
        }

        return new ParsedRecord(eventId, payload);
    }

    private AccountOpenedPayload extractPayload(JsonNode dataNode) {
        if (dataNode == null || !dataNode.isObject()) {
            throw new IllegalArgumentException("Invalid payload data: expected JSON object");
        }

        UUID accountId = getRequiredUuid(dataNode, "accountId");
        UUID customerId = getOptionalUuid(dataNode, "customerId");
        String customerCpf = getOptionalText(dataNode, "customerCpf");
        String customerName = getOptionalText(dataNode, "customerName");
        String branch = getRequiredText(dataNode, "branch");
        String accountNumber = getRequiredText(dataNode, "accountNumber");
        String checkDigit = getRequiredText(dataNode, "checkDigit");
        Instant openedAt = getOptionalInstant(dataNode, "openedAt");

        return new AccountOpenedPayload(
                accountId,
                customerId,
                customerCpf,
                customerName,
                branch,
                accountNumber,
                checkDigit,
                openedAt
        );
    }

    private UUID getRequiredUuid(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull() || node.get(fieldName).asString().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return UUID.fromString(node.get(fieldName).asString());
    }

    private UUID getOptionalUuid(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull() && !node.get(fieldName).asString().isBlank()) {
            return UUID.fromString(node.get(fieldName).asString());
        }
        return null;
    }

    private String getRequiredText(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return node.get(fieldName).asString();
    }

    private String getOptionalText(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asString();
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

    private record ParsedRecord(UUID eventId, AccountOpenedPayload payload) {}
}
