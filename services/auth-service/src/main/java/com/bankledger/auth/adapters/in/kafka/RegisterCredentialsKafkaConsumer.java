package com.bankledger.auth.adapters.in.kafka;

import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
import com.bankledger.commons.security.dto.RegisterCredentialsPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Inbound Kafka consumer adapter for credential registration events.
 * Parses messages using KafkaEventEnvelope<RegisterCredentialsPayload> and ObjectMapper (NO JsonNode).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterCredentialsKafkaConsumer {

    private final RegisterCredentialsUseCase registerCredentialsUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.credentials-registration:" + KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1 + "}",
            groupId = "${spring.kafka.consumer.group-id:auth-service-credentials-registration}"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record must not be null");

        log.info("Received credentials registration message: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            RegisterCredentialsPayload payload = parsePayload(record.value());

            if (payload == null) {
                log.warn("Payload resolved to null for key={}", record.key());
                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
                return;
            }

            try {
                registerCredentialsUseCase.registerCredentials(RegisterCredentialsCommand.fromPayload(payload));
                log.info("Successfully registered credentials via Kafka for customerId={}", payload.customerId());
            } catch (CredentialsAlreadyExistsException e) {
                log.warn("Credentials already exist for customerId={}, skipping idempotent message: {}",
                        payload.customerId(), e.getMessage());
            }

            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            log.error("Failed to process credentials registration message: key={}", record.key(), e);
            throw new RuntimeException("Failed to process credentials registration message", e);
        }
    }

    private RegisterCredentialsPayload parsePayload(String rawMessage) throws Exception {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }

        try {
            KafkaEventEnvelope<RegisterCredentialsPayload> envelope = objectMapper.readValue(
                    rawMessage,
                    new TypeReference<KafkaEventEnvelope<RegisterCredentialsPayload>>() {}
            );
            if (envelope != null && envelope.data() != null) {
                return envelope.data();
            }
        } catch (Exception e) {
            log.debug("Not a standard KafkaEventEnvelope, falling back to flat payload: {}", e.getMessage());
        }

        return objectMapper.readValue(rawMessage, RegisterCredentialsPayload.class);
    }
}
