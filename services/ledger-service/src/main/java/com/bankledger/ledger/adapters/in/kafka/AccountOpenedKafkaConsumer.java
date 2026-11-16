package com.bankledger.ledger.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
import com.bankledger.commons.events.v1.account.AccountOpenedPayload;
import com.bankledger.commons.inbox.InboxHandler;
import com.bankledger.ledger.application.port.in.ProvisionAccountUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
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
            containerFactory = "kafkaListenerContainerFactory", 
            concurrency = "3"
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

        AccountOpenedPayload payload = null;
        UUID eventId = null;

        try {
            KafkaEventEnvelope<AccountOpenedPayload> envelope = objectMapper.readValue(
                    value,
                    new TypeReference<KafkaEventEnvelope<AccountOpenedPayload>>() {}
            );
            if (envelope != null) {
                if (envelope.data() != null) {
                    payload = envelope.data();
                    eventId = envelope.resolveEventId();
                } else if (envelope.resolveEventId() != null) {
                    eventId = envelope.resolveEventId();
                }
            }
        } catch (Exception ignored) {
            // Envelope parsing failed or payload not nested under data; fallback to direct payload deserialization
        }

        if (payload == null) {
            try {
                payload = objectMapper.readValue(value, AccountOpenedPayload.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to deserialize AccountOpenedPayload: " + e.getMessage(), e);
            }
        }

        if (payload == null) {
            throw new IllegalArgumentException("AccountOpenedPayload must not be null");
        }

        if (eventId == null && record.headers() != null) {
            eventId = extractHeaderUuid(record, "ce_id")
                    .or(() -> extractHeaderUuid(record, "eventId"))
                    .or(() -> extractHeaderUuid(record, "id"))
                    .orElse(null);
        }

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
