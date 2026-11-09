package com.bankledger.commons.domain.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeTest {

    private ObjectMapper objectMapper;

    record AccountOpenedPayload(UUID accountId, String customerCpf, long initialBalanceInCents) {}

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
    }

    @Test
    @DisplayName("should create EventMetadata with provided fields and generate defaults when null")
    void shouldCreateEventMetadata() {
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-11-09T12:00:00Z");

        EventMetadata metadata = new EventMetadata(correlationId, causationId, userId, timestamp);

        assertThat(metadata.correlationId()).isEqualTo(correlationId);
        assertThat(metadata.causationId()).isEqualTo(causationId);
        assertThat(metadata.userId()).isEqualTo(userId);
        assertThat(metadata.timestamp()).isEqualTo(timestamp);

        // Factory create method
        EventMetadata created = EventMetadata.create(null, causationId, userId);
        assertThat(created.correlationId()).isNotNull();
        assertThat(created.causationId()).isEqualTo(causationId);
        assertThat(created.userId()).isEqualTo(userId);
        assertThat(created.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("should create EventEnvelope via factory create with CloudEvents 1.0 defaults")
    void shouldCreateEventEnvelopeWithDefaults() {
        AccountOpenedPayload payload = new AccountOpenedPayload(UUID.randomUUID(), "12345678901", 0L);
        EventMetadata metadata = EventMetadata.create(UUID.randomUUID(), null, UUID.randomUUID());

        EventEnvelope<AccountOpenedPayload> envelope = EventEnvelope.create(
                "bankledger/account-service",
                "AccountOpened",
                payload,
                metadata
        );

        assertThat(envelope.id()).isNotNull();
        assertThat(envelope.source()).isEqualTo("bankledger/account-service");
        assertThat(envelope.type()).isEqualTo("AccountOpened");
        assertThat(envelope.datacontenttype()).isEqualTo("application/json");
        assertThat(envelope.dataschema()).isNull();
        assertThat(envelope.specversion()).isEqualTo("1.0");
        assertThat(envelope.time()).isNotNull();
        assertThat(envelope.data()).isEqualTo(payload);
        assertThat(envelope.metadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("should validate mandatory fields when constructing EventEnvelope")
    void shouldValidateMandatoryFields() {
        AccountOpenedPayload payload = new AccountOpenedPayload(UUID.randomUUID(), "12345678901", 0L);
        EventMetadata metadata = EventMetadata.create(null, null, null);

        assertThatThrownBy(() -> new EventEnvelope<>(
                UUID.randomUUID(),
                null,
                "AccountOpened",
                "application/json",
                null,
                Instant.now(),
                "1.0",
                metadata,
                payload
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("source");

        assertThatThrownBy(() -> new EventEnvelope<>(
                UUID.randomUUID(),
                "bankledger/account-service",
                null,
                "application/json",
                null,
                Instant.now(),
                "1.0",
                metadata,
                payload
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("type");
    }

    @Test
    @DisplayName("should serialize EventEnvelope to CloudEvents 1.0 JSON with ISO-8601 UTC timestamp")
    void shouldSerializeToCloudEventsJson() throws Exception {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID correlationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID causationId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Instant fixedTime = Instant.parse("2026-11-09T18:30:00Z");

        EventMetadata metadata = new EventMetadata(correlationId, causationId, userId, fixedTime);
        AccountOpenedPayload payload = new AccountOpenedPayload(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "12345678901",
                10000L
        );

        EventEnvelope<AccountOpenedPayload> envelope = new EventEnvelope<>(
                eventId,
                "bankledger/account-service",
                "AccountOpened",
                "application/json",
                "https://bankledger.com/schemas/AccountOpened.json",
                fixedTime,
                "1.0",
                metadata,
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);

        assertThat(json).contains("\"specversion\":\"1.0\"");
        assertThat(json).contains("\"id\":\"11111111-1111-1111-1111-111111111111\"");
        assertThat(json).contains("\"source\":\"bankledger/account-service\"");
        assertThat(json).contains("\"type\":\"AccountOpened\"");
        assertThat(json).contains("\"datacontenttype\":\"application/json\"");
        assertThat(json).contains("\"dataschema\":\"https://bankledger.com/schemas/AccountOpened.json\"");
        assertThat(json).contains("\"time\":\"2026-11-09T18:30:00Z\"");
        assertThat(json).contains("\"correlationId\":\"22222222-2222-2222-2222-222222222222\"");
        assertThat(json).contains("\"causationId\":\"33333333-3333-3333-3333-333333333333\"");
        assertThat(json).contains("\"userId\":\"44444444-4444-4444-4444-444444444444\"");
        assertThat(json).contains("\"customerCpf\":\"12345678901\"");
        assertThat(json).contains("\"initialBalanceInCents\":10000");
    }

    @Test
    @DisplayName("should deserialize CloudEvents JSON back to EventEnvelope using Jackson 3")
    void shouldDeserializeFromCloudEventsJson() throws Exception {
        String json = """
                {
                  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "source": "bankledger/account-service",
                  "type": "AccountOpened",
                  "datacontenttype": "application/json",
                  "dataschema": "https://bankledger.com/schemas/AccountOpened.json",
                  "time": "2026-11-09T19:00:00Z",
                  "specversion": "1.0",
                  "metadata": {
                    "correlationId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "causationId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "userId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
                    "timestamp": "2026-11-09T19:00:00Z"
                  },
                  "data": {
                    "accountId": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                    "customerCpf": "98765432100",
                    "initialBalanceInCents": 5000
                  }
                }
                """;

        EventEnvelope<AccountOpenedPayload> envelope = objectMapper.readValue(
                json,
                new TypeReference<EventEnvelope<AccountOpenedPayload>>() {}
        );

        assertThat(envelope.id()).isEqualTo(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertThat(envelope.source()).isEqualTo("bankledger/account-service");
        assertThat(envelope.type()).isEqualTo("AccountOpened");
        assertThat(envelope.datacontenttype()).isEqualTo("application/json");
        assertThat(envelope.dataschema()).isEqualTo("https://bankledger.com/schemas/AccountOpened.json");
        assertThat(envelope.time()).isEqualTo(Instant.parse("2026-11-09T19:00:00Z"));
        assertThat(envelope.specversion()).isEqualTo("1.0");

        assertThat(envelope.metadata().correlationId()).isEqualTo(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        assertThat(envelope.metadata().causationId()).isEqualTo(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        assertThat(envelope.metadata().userId()).isEqualTo(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        assertThat(envelope.metadata().timestamp()).isEqualTo(Instant.parse("2026-11-09T19:00:00Z"));

        assertThat(envelope.data().accountId()).isEqualTo(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
        assertThat(envelope.data().customerCpf()).isEqualTo("98765432100");
        assertThat(envelope.data().initialBalanceInCents()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("should deserialize JSON with missing optional fields applying canonical defaults")
    void shouldDeserializeJsonWithMissingOptionalFields() throws Exception {
        String json = """
                {
                  "source": "bankledger/account-service",
                  "type": "AccountOpened",
                  "data": {
                    "accountId": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                    "customerCpf": "98765432100",
                    "initialBalanceInCents": 0
                  }
                }
                """;

        EventEnvelope<AccountOpenedPayload> envelope = objectMapper.readValue(
                json,
                new TypeReference<EventEnvelope<AccountOpenedPayload>>() {}
        );

        assertThat(envelope.id()).isNotNull();
        assertThat(envelope.source()).isEqualTo("bankledger/account-service");
        assertThat(envelope.type()).isEqualTo("AccountOpened");
        assertThat(envelope.datacontenttype()).isEqualTo("application/json");
        assertThat(envelope.dataschema()).isNull();
        assertThat(envelope.specversion()).isEqualTo("1.0");
        assertThat(envelope.time()).isNotNull();
        assertThat(envelope.metadata()).isNotNull();
        assertThat(envelope.metadata().correlationId()).isNotNull();
        assertThat(envelope.data().customerCpf()).isEqualTo("98765432100");
    }
}
