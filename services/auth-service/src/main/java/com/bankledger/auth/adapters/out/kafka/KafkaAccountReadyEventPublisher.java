package com.bankledger.auth.adapters.out.kafka;

import com.bankledger.auth.application.port.out.AccountReadyEventPublisherPort;
import com.bankledger.auth.domain.event.AccountReadyEvent;
import com.bankledger.commons.constant.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Reactive outbound Kafka adapter that publishes AccountReadyEvent wrapped in
 * KafkaEventEnvelope<AccountReadyPayload> to topic 'account.ready.v1'.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAccountReadyEventPublisher implements AccountReadyEventPublisherPort {

    private final Optional<KafkaTemplate<String, String>> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> publish(AccountReadyEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (kafkaTemplate.isEmpty()) {
            log.warn("KafkaTemplate not available, skipping AccountReadyEvent publish for eventId: {}", event.eventId());
            return Mono.empty();
        }

        return Mono.<Void>fromRunnable(() -> {
            try {
                String messageKey = event.payload().customerId().toString();
                String json = objectMapper.writeValueAsString(event.toEnvelope());
                kafkaTemplate.get().send(KafkaTopics.ACCOUNT_READY_V1, messageKey, json)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish AccountReadyEvent to topic '{}' for customerId: {}",
                                        KafkaTopics.ACCOUNT_READY_V1, event.payload().customerId(), ex);
                            } else {
                                log.info("Published AccountReadyEvent to topic '{}' with key '{}', eventId: {}",
                                        KafkaTopics.ACCOUNT_READY_V1, event.payload().customerId(), event.eventId());
                            }
                        });
            } catch (Exception e) {
                log.error("Failed to publish AccountReadyEvent for customerId: {}", event.payload().customerId(), e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(Duration.ofMillis(500))
        .onErrorResume(ex -> {
            log.warn("AccountReadyEvent publishing timed out or failed: {}", ex.getMessage());
            return Mono.empty();
        });
    }
}
