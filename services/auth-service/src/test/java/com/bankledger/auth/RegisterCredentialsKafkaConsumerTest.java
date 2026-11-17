package com.bankledger.auth;

import com.bankledger.auth.adapters.in.kafka.RegisterCredentialsKafkaConsumer;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.KafkaEventEnvelope;
import com.bankledger.commons.security.dto.RegisterCredentialsPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterCredentialsKafkaConsumerTest {

    @Mock
    private RegisterCredentialsUseCase registerCredentialsUseCase;

    @Mock
    private Acknowledgment acknowledgment;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private RegisterCredentialsKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RegisterCredentialsKafkaConsumer(registerCredentialsUseCase, objectMapper);
    }

    @Test
    @DisplayName("Should successfully consume KafkaEventEnvelope<RegisterCredentialsPayload>, register credentials and acknowledge")
    void shouldConsumeEnvelopeAndRegisterCredentials() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        RegisterCredentialsPayload payload = new RegisterCredentialsPayload(
                customerId,
                "52998224725",
                "StrongPass@123",
                accountId,
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
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

        String json = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1,
                0,
                100L,
                customerId.toString(),
                json
        );

        AuthUser mockUser = new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$2a$10$hashedpassword",
                "ROLE_USER",
                customerId,
                accountId,
                "0001",
                "123456",
                0,
                null,
                "Carlos Silva",
                "carlos@example.com",
                Instant.now(),
                Instant.now()
        );
        when(registerCredentialsUseCase.registerCredentials(any(RegisterCredentialsCommand.class))).thenReturn(Mono.just(mockUser));

        consumer.onMessage(record, acknowledgment);

        ArgumentCaptor<RegisterCredentialsCommand> commandCaptor = ArgumentCaptor.forClass(RegisterCredentialsCommand.class);
        verify(registerCredentialsUseCase).registerCredentials(commandCaptor.capture());

        RegisterCredentialsCommand captured = commandCaptor.getValue();
        assertThat(captured.customerId()).isEqualTo(customerId);
        assertThat(captured.cpf()).isEqualTo("52998224725");
        assertThat(captured.password()).isEqualTo("StrongPass@123");
        assertThat(captured.fullName()).isEqualTo("Carlos Silva");
        assertThat(captured.email()).isEqualTo("carlos@example.com");

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle duplicate registration gracefully and acknowledge offset (idempotency)")
    void shouldHandleDuplicateRegistrationGracefully() throws Exception {
        UUID customerId = UUID.randomUUID();
        RegisterCredentialsPayload payload = new RegisterCredentialsPayload(
                customerId,
                "52998224725",
                "StrongPass@123",
                UUID.randomUUID(),
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        );

        KafkaEventEnvelope<RegisterCredentialsPayload> envelope = new KafkaEventEnvelope<>(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "account-service",
                "AccountOpened",
                "application/json",
                "1.0",
                Instant.now(),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1,
                0,
                101L,
                customerId.toString(),
                json
        );

        when(registerCredentialsUseCase.registerCredentials(any(RegisterCredentialsCommand.class)))
                .thenReturn(Mono.error(new CredentialsAlreadyExistsException("Already exists")));

        consumer.onMessage(record, acknowledgment);

        verify(registerCredentialsUseCase).registerCredentials(any(RegisterCredentialsCommand.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should parse direct flat RegisterCredentialsPayload fallback and acknowledge")
    void shouldParseFlatPayloadFallback() throws Exception {
        UUID customerId = UUID.randomUUID();
        RegisterCredentialsPayload payload = new RegisterCredentialsPayload(
                customerId,
                "52998224725",
                "StrongPass@123",
                UUID.randomUUID(),
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        );

        String json = objectMapper.writeValueAsString(payload);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1,
                0,
                102L,
                customerId.toString(),
                json
        );

        when(registerCredentialsUseCase.registerCredentials(any(RegisterCredentialsCommand.class)))
                .thenReturn(Mono.empty());

        consumer.onMessage(record, acknowledgment);

        verify(registerCredentialsUseCase).registerCredentials(any(RegisterCredentialsCommand.class));
        verify(acknowledgment).acknowledge();
    }
}
