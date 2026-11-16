package com.bankledger.query.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
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
import tools.jackson.core.type.TypeReference;
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
            String value = record.value();
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Kafka record value must not be empty");
            }

            AccountOpenedPayload payload = null;
            UUID eventId = null;

            try {
                KafkaEventEnvelope<AccountOpenedPayload> envelope = objectMapper.readValue(
                        value,
                        new TypeReference<KafkaEventEnvelope<AccountOpenedPayload>>() {}
                );
                if (envelope != null && envelope.data() != null) {
                    payload = envelope.data();
                    eventId = envelope.resolveEventId();
                } else {
                    payload = objectMapper.readValue(value, AccountOpenedPayload.class);
                }
            } catch (Exception ex) {
                payload = objectMapper.readValue(value, AccountOpenedPayload.class);
            }

            if (payload == null) {
                throw new IllegalArgumentException("AccountOpened payload must not be null");
            }

            if (eventId == null) {
                eventId = resolveAccountOpenedEventId(record, payload);
            }

            final AccountOpenedPayload finalPayload = payload;
            inboxHandler.execute(eventId, HANDLER_ACCOUNT_OPENED, () -> {
                initializeZeroBalance(finalPayload.accountId(), finalPayload.openedAt());
            });

            acknowledgment.acknowledge();
            log.info("Acknowledged AccountOpened record for accountId={}, eventId={}", payload.accountId(), eventId);
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
            String value = record.value();
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Kafka record value must not be empty");
            }

            EntryPostedInboundDto dto = null;
            UUID eventId = null;

            try {
                KafkaEventEnvelope<EntryPostedInboundDto> envelope = objectMapper.readValue(
                        value,
                        new TypeReference<KafkaEventEnvelope<EntryPostedInboundDto>>() {}
                );
                if (envelope != null && envelope.data() != null) {
                    dto = envelope.data();
                    eventId = envelope.resolveEventId();
                } else {
                    dto = objectMapper.readValue(value, EntryPostedInboundDto.class);
                }
            } catch (Exception ex) {
                dto = objectMapper.readValue(value, EntryPostedInboundDto.class);
            }

            if (dto == null) {
                throw new IllegalArgumentException("EntryPosted payload must not be null");
            }

            if (eventId == null) {
                eventId = resolveEntryPostedEventId(record, dto);
            }

            final UUID finalEventId = eventId;
            final EntryPostedInboundDto finalDto = dto;
            inboxHandler.execute(finalEventId, HANDLER_ENTRY_POSTED, () -> {
                processEntryPosted(finalDto, finalEventId);
            });

            acknowledgment.acknowledge();
            log.info("Acknowledged EntryPosted record for eventId={}", finalEventId);
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
    public void processEntryPosted(EntryPostedInboundDto dto, UUID fallbackId) {
        UUID entryId = dto.entryId();
        if (entryId == null) {
            entryId = dto.id();
        }
        if (entryId == null) {
            entryId = fallbackId != null ? fallbackId : UUID.randomUUID();
        }

        final UUID accountId = dto.accountId() != null ? dto.accountId() : dto.ledgerAccountId();
        if (accountId == null) {
            throw new IllegalArgumentException("EntryPosted payload missing accountId / ledgerAccountId");
        }

        UUID journalId = dto.journalId() != null ? dto.journalId() : UUID.randomUUID();
        UUID transactionId = dto.transactionId() != null ? dto.transactionId() : UUID.randomUUID();

        String direction = dto.direction();
        if (direction == null || direction.isBlank()) {
            direction = dto.entryType();
        }
        if (direction == null || direction.isBlank()) {
            direction = "CREDIT";
        } else {
            direction = direction.trim().toUpperCase();
        }

        long amount = dto.amount();
        long sequence = dto.sequence();

        Instant postedAt = dto.postedAt();
        if (postedAt == null) {
            postedAt = dto.timestamp();
        }
        if (postedAt == null) {
            postedAt = dto.createdAt();
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
        if (dto.balanceAfter() != null) {
            balanceAfter = dto.balanceAfter();
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

        String category = dto.category() != null ? dto.category().trim() : "";
        String type = dto.type() != null ? dto.type().trim() : "";
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

        String description = dto.description() != null ? dto.description().trim() : "";
        if (description.isBlank()) {
            description = "CREDIT".equalsIgnoreCase(direction) ? "Depósito em conta" : "Saque em conta";
        }

        String counterpartyName = dto.counterpartyName();
        String counterpartyDocumentMasked = dto.counterpartyDocumentMasked();
        String counterpartyAccount = dto.counterpartyAccount();
        String idempotencyKey = dto.idempotencyKey();
        String failureReason = dto.failureReason();

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

    private UUID resolveAccountOpenedEventId(ConsumerRecord<String, String> record, AccountOpenedPayload payload) {
        if (record.headers() != null) {
            Optional<UUID> headerId = extractHeaderUuid(record, "ce_id")
                    .or(() -> extractHeaderUuid(record, "eventId"))
                    .or(() -> extractHeaderUuid(record, "id"));
            if (headerId.isPresent()) {
                return headerId.get();
            }
        }

        if (payload != null && payload.accountId() != null) {
            return payload.accountId();
        }

        if (record.key() != null) {
            UUID keyId = parseUuid(record.key());
            if (keyId != null) {
                return keyId;
            }
        }

        return UUID.randomUUID();
    }

    private UUID resolveEntryPostedEventId(ConsumerRecord<String, String> record, EntryPostedInboundDto dto) {
        if (dto != null) {
            if (dto.id() != null) {
                return dto.id();
            }
            if (dto.entryId() != null) {
                return dto.entryId();
            }
        }

        if (record.headers() != null) {
            Optional<UUID> headerId = extractHeaderUuid(record, "ce_id")
                    .or(() -> extractHeaderUuid(record, "eventId"))
                    .or(() -> extractHeaderUuid(record, "id"));
            if (headerId.isPresent()) {
                return headerId.get();
            }
        }

        if (record.key() != null) {
            UUID keyId = parseUuid(record.key());
            if (keyId != null) {
                return keyId;
            }
        }

        return UUID.randomUUID();
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
}
