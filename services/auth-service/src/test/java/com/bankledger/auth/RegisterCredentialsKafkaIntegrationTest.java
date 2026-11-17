package com.bankledger.auth;

import com.bankledger.auth.application.port.out.AuthUserRepositoryPort;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
import com.bankledger.commons.security.dto.RegisterCredentialsPayload;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 3,
        topics = {
                KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1,
                KafkaTopics.ACCOUNT_READY_V1
        }
)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class RegisterCredentialsKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AuthUserRepositoryPort authUserRepositoryPort;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should receive credentials registration message from Kafka and save user in auth-service")
    void shouldReceiveAndProcessCredentialsRegistration() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String cpf = "98765432100";
        String password = "SecurePassword@123";

        RegisterCredentialsPayload payload = new RegisterCredentialsPayload(
                customerId,
                cpf,
                password,
                accountId,
                "0001",
                "999888",
                "Integration Test User",
                "integration.test@bank.internal"
        );

        String jsonPayload = objectMapper.writeValueAsString(payload);

        // Send message to topic as produced by account-service outbox
        kafkaTemplate.send(KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1, customerId.toString(), jsonPayload).get();

        // Await consumption and persistence in auth-service
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Optional<AuthUser> user = authUserRepositoryPort.findByCpf(cpf).blockOptional();
                    assertThat(user).isPresent();
                    assertThat(user.get().getCustomerId()).isEqualTo(customerId);
                    assertThat(user.get().getAccountId()).isEqualTo(accountId);
                    assertThat(user.get().getEmail()).isEqualTo("integration.test@bank.internal");
                    assertThat(user.get().getFullName()).isEqualTo("Integration Test User");
                });
    }

    @Test
    @DisplayName("Should receive credentials registration message wrapped in KafkaEventEnvelope and save user in auth-service")
    void shouldReceiveAndProcessEnvelopeCredentialsRegistration() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String cpf = "11223344556";
        String password = "SecurePassword@456";

        RegisterCredentialsPayload payload = new RegisterCredentialsPayload(
                customerId,
                cpf,
                password,
                accountId,
                "0001",
                "888777",
                "Envelope User",
                "envelope.user@bank.internal"
        );

        UUID eventId = UUID.randomUUID();
        KafkaEventEnvelope<RegisterCredentialsPayload> envelope = new KafkaEventEnvelope<>(
                eventId,
                eventId,
                "account-service",
                "AccountOpened",
                "application/json",
                "1.0",
                Instant.now(),
                payload
        );

        String jsonEnvelope = objectMapper.writeValueAsString(envelope);

        kafkaTemplate.send(KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1, customerId.toString(), jsonEnvelope).get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Optional<AuthUser> user = authUserRepositoryPort.findByCpf(cpf).blockOptional();
                    assertThat(user).isPresent();
                    assertThat(user.get().getCustomerId()).isEqualTo(customerId);
                });
    }
}
