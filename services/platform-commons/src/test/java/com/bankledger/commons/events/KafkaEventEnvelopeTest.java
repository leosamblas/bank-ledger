package com.bankledger.commons.events;

import com.bankledger.commons.events.v1.account.AccountOpenedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaEventEnvelope Unit Tests")
class KafkaEventEnvelopeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
    }

    @Test
    @DisplayName("Should deserialize CloudEvents envelope JSON with typed payload and ignore unknown properties")
    void shouldDeserializeCloudEventsEnvelope() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String json = String.format("""
                {
                  "id": "%s",
                  "source": "account-service",
                  "type": "AccountOpened",
                  "datacontenttype": "application/json",
                  "specversion": "1.0",
                  "time": "2026-11-13T10:00:00Z",
                  "unknownField": "shouldBeIgnored",
                  "data": {
                    "accountId": "%s",
                    "customerId": "%s",
                    "customerCpf": "12345678900",
                    "customerName": "Maria Silva",
                    "branch": "0001",
                    "accountNumber": "100000",
                    "checkDigit": "1",
                    "openedAt": "2026-11-13T10:00:00Z"
                  }
                }
                """, eventId, accountId, customerId);

        KafkaEventEnvelope<AccountOpenedPayload> envelope = objectMapper.readValue(
                json,
                new TypeReference<KafkaEventEnvelope<AccountOpenedPayload>>() {}
        );

        assertThat(envelope).isNotNull();
        assertThat(envelope.resolveEventId()).isEqualTo(eventId);
        assertThat(envelope.source()).isEqualTo("account-service");
        assertThat(envelope.type()).isEqualTo("AccountOpened");
        assertThat(envelope.data()).isNotNull();
        assertThat(envelope.data().accountId()).isEqualTo(accountId);
        assertThat(envelope.data().customerName()).isEqualTo("Maria Silva");
    }

    @Test
    @DisplayName("Should deserialize flat JSON without data field and produce null data")
    void shouldDeserializeFlatJsonWithNullData() throws Exception {
        UUID accountId = UUID.randomUUID();
        String json = String.format("""
                {
                  "accountId": "%s",
                  "customerName": "Flat Payload"
                }
                """, accountId);

        KafkaEventEnvelope<AccountOpenedPayload> envelope = objectMapper.readValue(
                json,
                new TypeReference<KafkaEventEnvelope<AccountOpenedPayload>>() {}
        );

        assertThat(envelope).isNotNull();
        assertThat(envelope.data()).isNull();
        assertThat(envelope.resolveEventId()).isNull();
    }
}
