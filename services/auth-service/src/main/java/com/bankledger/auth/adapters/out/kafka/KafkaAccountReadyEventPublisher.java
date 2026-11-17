package com.bankledger.auth.adapters.out.kafka;

import com.bankledger.auth.application.port.out.AccountReadyEventPublisherPort;
import com.bankledger.auth.domain.event.AccountReadyEvent;
import com.bankledger.commons.constant.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Optional;

/**
 * Outbound Kafka adapter that publishes AccountReadyEvent wrapped in
 * KafkaEventEnvelope<AccountReadyPayload> to topic 'account.ready.v1'.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAccountReadyEventPublisher implements AccountReadyEventPublisherPort {

    private final Optional<KafkaTemplate<String, String>> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(AccountReadyEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (kafkaTemplate.isEmpty()) {
            log.warn("KafkaTemplate not available, skipping AccountReadyEvent publish for eventId: {}", event.eventId());
            return;
        }

        try {
            String messageKey = event.payload().customerId().toString();
            String json = objectMapper.writeValueAsString(event.toEnvelope());
            kafkaTemplate.get().send(KafkaTopics.ACCOUNT_READY_V1, messageKey, json);
            log.info("Published AccountReadyEvent to topic '{}' with key '{}', eventId: {}",
                    KafkaTopics.ACCOUNT_READY_V1, messageKey, event.eventId());
        } catch (Exception e) {
            log.error("Failed to publish AccountReadyEvent to topic '{}' for customerId: {}",
                    KafkaTopics.ACCOUNT_READY_V1, event.payload().customerId(), e);
            throw new RuntimeException("Failed to publish AccountReadyEvent", e);
        }
    }
}
