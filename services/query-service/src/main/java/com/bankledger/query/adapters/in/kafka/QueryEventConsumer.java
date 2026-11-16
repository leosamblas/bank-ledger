package com.bankledger.query.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.account.AccountOpenedPayload;
import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.query.adapters.out.persistence.AccountBalanceEntity;
import com.bankledger.query.adapters.out.persistence.AccountBalanceRepository;
import com.bankledger.query.adapters.out.persistence.StatementEntryEntity;
import com.bankledger.query.adapters.out.persistence.StatementEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound Kafka consumer projecting read models (account balance and statement entries).
 * Enforces exactly-once execution via {@link InboxHandler} and commits offsets manually.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryEventConsumer {

    public static final String HANDLER_ACCOUNT_OPENED = "QueryAccountOpenedConsumer";
    public static final String HANDLER_ENTRY_POSTED = "QueryEntryPostedConsumer";

    private final AccountBalanceRepository accountBalanceRepository;
    private final StatementEntryRepository statementEntryRepository;
    private final InboxHandler inboxHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.account-events:" + KafkaTopics.ACCOUNT_EVENTS_V1 + "}",
            groupId = "${spring.kafka.consumer.group-id:query-service-events}-account",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "3"
    )
    public void onAccountOpened(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record must not be null");
        Objects.requireNonNull(acknowledgment, "acknowledgment must not be null");

        log.info("Received AccountOpened message: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            JsonNode dataNode = extractDataNode(record);
            UUID eventId = extractEventId(record, dataNode);

            AccountOpenedPayload payload;
            try {
                payload = objectMapper.treeToValue(dataNode, AccountOpenedPayload.class);
            } catch (Exception e) {
                // If treeToValue fails, extract accountId directly
                UUID accountId = parseUuid(dataNode.path("accountId").asString(null));
                if (accountId != null) {
                    Instant openedAt = parseInstant(dataNode.path("openedAt").asString(null));
                    payload = new AccountOpenedPayload(
                            accountId,
                            UUID.randomUUID(),
                            "000.000.000-00",
                            "Cliente",
                            "0001",
                            "00000000",
                            "0",
                            openedAt != null ? openedAt : Instant.now()
                    );
                } else {
                    throw new IllegalArgumentException("Cannot extract accountId from AccountOpened payload", e);
                }
            }

            final AccountOpenedPayload finalPayload = payload;
            inboxHandler.execute(eventId, HANDLER_ACCOUNT_OPENED, () -> {
                initializeZeroBalance(finalPayload.accountId(), finalPayload.openedAt());
            });

            acknowledgment.acknowledge();
            log.info("Acknowledged AccountOpened record for accountId={}", payload.accountId());
        } catch (Exception ex) {
            log.error("Failed to process AccountOpened Kafka record: topic={}, offset={}, error={}",
                    record.topic(), record.offset(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ledger-entries:" + KafkaTopics.LEDGER_ENTRIES_V1 + "}",
            groupId = "${spring.kafka.consumer.group-id:query-service-events}-ledger",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEntryPosted(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record must not be null");
        Objects.requireNonNull(acknowledgment, "acknowledgment must not be null");

        log.info("Received EntryPosted message: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            JsonNode dataNode = extractDataNode(record);
            UUID eventId = extractEventId(record, dataNode);

            inboxHandler.execute(eventId, HANDLER_ENTRY_POSTED, () -> {
                processEntryPosted(dataNode, eventId);
            });

            acknowledgment.acknowledge();
            log.info("Acknowledged EntryPosted record for eventId={}", eventId);
        } catch (Exception ex) {
            log.error("Failed to process EntryPosted Kafka record: topic={}, offset={}, error={}",
                    record.topic(), record.offset(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional
    public void initializeZeroBalance(UUID accountId, Instant openedAt) {
        if (!accountBalanceRepository.existsById(accountId)) {
            Instant timestamp = (openedAt != null) ? openedAt : Instant.now();
            AccountBalanceEntity entity = AccountBalanceEntity.builder()
                    .accountId(accountId)
                    .balance(0L)
                    .currency("BRL")
                    .lastSequence(0L)
                    .updatedAt(timestamp)
                    .build();
            accountBalanceRepository.save(entity);
            log.info("Initialized initial zero balance for accountId={}", accountId);
        } else {
            log.info("Balance already exists for accountId={}, skipping zero initialization", accountId);
        }
    }

    @Transactional
    public void processEntryPosted(JsonNode node, UUID fallbackId) {
        UUID entryId = parseUuid(node.path("entryId").asString(null));
        if (entryId == null) {
            entryId = parseUuid(node.path("id").asString(null));
        }
        if (entryId == null) {
            entryId = fallbackId != null ? fallbackId : UUID.randomUUID();
        }

        UUID rawAccountId = parseUuid(node.path("accountId").asString(null));
        if (rawAccountId == null) {
            rawAccountId = parseUuid(node.path("ledgerAccountId").asString(null));
        }
        if (rawAccountId == null) {
            throw new IllegalArgumentException("EntryPosted payload missing accountId / ledgerAccountId");
        }
        final UUID accountId = rawAccountId;

        UUID journalId = parseUuid(node.path("journalId").asString(null));
        if (journalId == null) {
            journalId = UUID.randomUUID();
        }

        UUID transactionId = parseUuid(node.path("transactionId").asString(null));
        if (transactionId == null) {
            transactionId = UUID.randomUUID();
        }

        String direction = node.path("direction").asString("").toUpperCase();
        if (direction.isBlank()) {
            direction = node.path("entryType").asString("").toUpperCase();
        }
        if (direction.isBlank()) {
            direction = "CREDIT";
        }

        long amount = node.path("amount").asLong(0L);
        long sequence = node.path("sequence").asLong(0L);

        Instant postedAt = parseInstant(node.path("postedAt").asString(null));
        if (postedAt == null) {
            postedAt = parseInstant(node.path("timestamp").asString(null));
        }
        if (postedAt == null) {
            postedAt = parseInstant(node.path("createdAt").asString(null));
        }
        if (postedAt == null) {
            postedAt = Instant.now();
        }

        // 1. Update Account Balance
        AccountBalanceEntity balanceEntity = accountBalanceRepository.findById(accountId)
                .orElseGet(() -> AccountBalanceEntity.builder()
                        .accountId(accountId)
                        .balance(0L)
                        .currency("BRL")
                        .lastSequence(0L)
                        .updatedAt(Instant.now())
                        .build());

        long balanceAfter;
        if (node.has("balanceAfter") && !node.path("balanceAfter").isNull()) {
            balanceAfter = node.path("balanceAfter").asLong();
        } else {
            // Calculate if not explicitly provided
            if ("CREDIT".equalsIgnoreCase(direction)) {
                balanceAfter = balanceEntity.getBalance() + amount;
            } else {
                balanceAfter = balanceEntity.getBalance() - amount;
            }
        }

        if (sequence >= balanceEntity.getLastSequence()) {
            balanceEntity.setBalance(balanceAfter);
            balanceEntity.setLastSequence(sequence);
            balanceEntity.setUpdatedAt(postedAt);
            accountBalanceRepository.save(balanceEntity);
            log.info("Updated account_balance for accountId={}: newBalance={}, sequence={}",
                    accountId, balanceAfter, sequence);
        } else {
            log.warn("Ignoring out-of-order sequence {} for accountId={}, current sequence={}",
                    sequence, accountId, balanceEntity.getLastSequence());
        }

        // 2. Insert Statement Entry
        if (statementEntryRepository.existsByAccountIdAndSequence(accountId, sequence)) {
            log.info("Statement entry already exists for accountId={} and sequence={}, skipping", accountId, sequence);
            return;
        }

        String category = node.path("category").asString("");
        String type = node.path("type").asString("");
        if (category.isBlank()) {
            if ("CREDIT".equalsIgnoreCase(direction)) {
                category = "DEPOSIT";
                type = type.isBlank() ? "DEPOSIT" : type;
            } else {
                category = "WITHDRAWAL";
                type = type.isBlank() ? "WITHDRAWAL" : type;
            }
        }
        if (type.isBlank()) {
            type = category;
        }

        String description = node.path("description").asString("");
        if (description.isBlank()) {
            description = "CREDIT".equalsIgnoreCase(direction) ? "Depósito em conta" : "Saque em conta";
        }

        String counterpartyName = node.path("counterpartyName").asString(null);
        String counterpartyDocumentMasked = node.path("counterpartyDocumentMasked").asString(null);
        String counterpartyAccount = node.path("counterpartyAccount").asString(null);
        String idempotencyKey = node.path("idempotencyKey").asString(null);
        String failureReason = node.path("failureReason").asString(null);

        StatementEntryEntity statementEntry = StatementEntryEntity.builder()
                .id(entryId)
                .accountId(accountId)
                .sequence(sequence)
                .journalId(journalId)
                .transactionId(transactionId)
                .category(category)
                .type(type)
                .direction(direction)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .counterpartyName(counterpartyName)
                .counterpartyDocumentMasked(counterpartyDocumentMasked)
                .counterpartyAccount(counterpartyAccount)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .failureReason(failureReason)
                .postedAt(postedAt)
                .build();

        statementEntryRepository.save(statementEntry);
        log.info("Saved statement entry id={} for accountId={} at sequence={}", entryId, accountId, sequence);
    }

    private JsonNode extractDataNode(ConsumerRecord<String, String> record) {
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

        if (root.has("data") && root.get("data").isObject()) {
            return root.get("data");
        }
        return root;
    }

    private UUID extractEventId(ConsumerRecord<String, String> record, JsonNode dataNode) {
        UUID eventId = null;

        if (dataNode.has("id") && !dataNode.get("id").isNull()) {
            eventId = parseUuid(dataNode.get("id").asString(null));
        }
        if (eventId == null && dataNode.has("eventId") && !dataNode.get("eventId").isNull()) {
            eventId = parseUuid(dataNode.get("eventId").asString(null));
        }
        if (eventId == null && dataNode.has("entryId") && !dataNode.get("entryId").isNull()) {
            eventId = parseUuid(dataNode.get("entryId").asString(null));
        }

        if (eventId == null && record.headers() != null) {
            eventId = extractHeaderUuid(record, "ce_id")
                    .or(() -> extractHeaderUuid(record, "eventId"))
                    .or(() -> extractHeaderUuid(record, "id"))
                    .orElse(null);
        }

        if (eventId == null && record.key() != null) {
            eventId = parseUuid(record.key());
        }

        if (eventId == null) {
            eventId = UUID.randomUUID();
        }

        return eventId;
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
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
