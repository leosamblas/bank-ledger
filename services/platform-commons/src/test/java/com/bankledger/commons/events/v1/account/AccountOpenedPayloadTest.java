package com.bankledger.commons.events.v1.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountOpenedPayload Canonical Record Tests")
class AccountOpenedPayloadTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
    }

    @Test
    @DisplayName("Should successfully construct AccountOpenedPayload when all fields are present")
    void shouldConstructValidPayload() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant openedAt = Instant.now();

        AccountOpenedPayload payload = new AccountOpenedPayload(
                accountId,
                customerId,
                "12345678901",
                "Maria da Silva",
                "0001",
                "123456",
                "7",
                openedAt
        );

        assertThat(payload.accountId()).isEqualTo(accountId);
        assertThat(payload.customerId()).isEqualTo(customerId);
        assertThat(payload.customerCpf()).isEqualTo("12345678901");
        assertThat(payload.customerName()).isEqualTo("Maria da Silva");
        assertThat(payload.branch()).isEqualTo("0001");
        assertThat(payload.accountNumber()).isEqualTo("123456");
        assertThat(payload.checkDigit()).isEqualTo("7");
        assertThat(payload.openedAt()).isEqualTo(openedAt);
    }

    @Test
    @DisplayName("Should serialize to JSON and deserialize back to AccountOpenedPayload")
    void shouldSerializeAndDeserializeJson() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-11-13T12:00:00Z");

        AccountOpenedPayload payload = new AccountOpenedPayload(
                accountId,
                customerId,
                "12345678901",
                "Maria da Silva",
                "0001",
                "123456",
                "7",
                openedAt
        );

        String json = objectMapper.writeValueAsString(payload);
        AccountOpenedPayload deserialized = objectMapper.readValue(json, AccountOpenedPayload.class);

        assertThat(deserialized).isEqualTo(payload);
    }

    @Test
    @DisplayName("Should implement Java Serializable")
    void shouldSupportJavaSerialization() throws Exception {
        AccountOpenedPayload payload = new AccountOpenedPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "12345678901",
                "Maria da Silva",
                "0001",
                "123456",
                "7",
                Instant.now()
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(payload);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            Object deserialized = ois.readObject();
            assertThat(deserialized).isEqualTo(payload);
        }
    }

    @Test
    @DisplayName("Should throw NullPointerException when any required field is null")
    void shouldValidateNullInvariants() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> new AccountOpenedPayload(null, id, "cpf", "name", "0001", "123", "4", now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("accountId");

        assertThatThrownBy(() -> new AccountOpenedPayload(id, null, "cpf", "name", "0001", "123", "4", now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("customerId");

        assertThatThrownBy(() -> new AccountOpenedPayload(id, id, null, "name", "0001", "123", "4", now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("customerCpf");

        assertThatThrownBy(() -> new AccountOpenedPayload(id, id, "cpf", null, "0001", "123", "4", now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("customerName");

        assertThatThrownBy(() -> new AccountOpenedPayload(id, id, "cpf", "name", null, "123", "4", now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("branch");

        assertThatThrownBy(() -> new AccountOpenedPayload(id, id, "cpf", "name", "0001", null, "4", now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("accountNumber");

        assertThatThrownBy(() -> new AccountOpenedPayload(id, id, "cpf", "name", "0001", "123", null, now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("checkDigit");

        assertThatThrownBy(() -> new AccountOpenedPayload(id, id, "cpf", "name", "0001", "123", "4", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("openedAt");
    }
}
