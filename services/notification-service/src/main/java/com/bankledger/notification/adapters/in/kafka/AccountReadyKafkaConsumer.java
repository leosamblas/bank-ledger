package com.bankledger.notification.adapters.in.kafka;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import com.bankledger.notification.application.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountReadyKafkaConsumer {

    private final EmailNotificationService emailNotificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.account-ready:" + KafkaTopics.ACCOUNT_READY_V1 + "}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
            concurrency = "3"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record must not be null");
        Objects.requireNonNull(acknowledgment, "acknowledgment must not be null");

        log.info("Received AccountReady message: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            AccountReadyPayload payload = parsePayload(record.value());
            emailNotificationService.sendAccountReadyEmail(payload).block();
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged AccountReady event for customerId={}, accountId={}",
                    payload.customerId(), payload.accountId());
        } catch (Exception ex) {
            log.error("Failed to process AccountReady Kafka record: topic={}, offset={}, key={}, error={}",
                    record.topic(), record.offset(), record.key(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private AccountReadyPayload parsePayload(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Kafka record value must not be empty");
        }

        AccountReadyPayload payload = null;

        try {
            KafkaEventEnvelope<AccountReadyPayload> envelope = objectMapper.readValue(
                    value,
                    new TypeReference<KafkaEventEnvelope<AccountReadyPayload>>() {}
            );
            if (envelope != null && envelope.data() != null) {
                payload = envelope.data();
            }
        } catch (Exception ignored) {
            // Envelope parsing failed; fallback to direct payload deserialization below
        }

        if (payload == null) {
            try {
                payload = objectMapper.readValue(value, AccountReadyPayload.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to deserialize AccountReadyPayload: " + e.getMessage(), e);
            }
        }

        return payload;
    }
}
