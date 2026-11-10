package com.bankledger.account.integration;

import com.bankledger.account.adapters.out.kafka.OutboxPollerLifecycle;
import com.bankledger.account.adapters.out.persistence.repository.AccountSpringDataRepository;
import com.bankledger.account.adapters.out.persistence.repository.CustomerSpringDataRepository;
import com.bankledger.account.application.port.in.CreateAccountCommand;
import com.bankledger.account.application.port.in.CreateAccountUseCase;
import com.bankledger.account.application.port.in.CreatedAccountResult;
import com.bankledger.account.domain.event.AccountOpenedEvent;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.outbox.OutboxEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OutboxKafkaIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("account_db")
            .withUsername("bankledger")
            .withPassword("bankledger");

    @Container
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:8.4.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("bankledger.outbox.poller.auto-startup", () -> "false");
    }

    private final CreateAccountUseCase createAccountUseCase;
    private final OutboxEventRepository outboxEventRepository;
    private final AccountSpringDataRepository accountSpringDataRepository;
    private final CustomerSpringDataRepository customerSpringDataRepository;
    private final OutboxPollerLifecycle outboxPollerLifecycle;
    private final ObjectMapper objectMapper;
    private KafkaConsumer<String, String> consumer;

    public OutboxKafkaIntegrationTest(
            CreateAccountUseCase createAccountUseCase,
            OutboxEventRepository outboxEventRepository,
            AccountSpringDataRepository accountSpringDataRepository,
            CustomerSpringDataRepository customerSpringDataRepository,
            OutboxPollerLifecycle outboxPollerLifecycle,
            ObjectMapper objectMapper
    ) {
        this.createAccountUseCase = createAccountUseCase;
        this.outboxEventRepository = outboxEventRepository;
        this.accountSpringDataRepository = accountSpringDataRepository;
        this.customerSpringDataRepository = customerSpringDataRepository;
        this.outboxPollerLifecycle = outboxPollerLifecycle;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        accountSpringDataRepository.deleteAll();
        customerSpringDataRepository.deleteAll();

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(AccountOpenedEvent.TOPIC));
    }

    @AfterEach
    void tearDown() {
        if (outboxPollerLifecycle != null && outboxPollerLifecycle.isRunning()) {
            outboxPollerLifecycle.stop();
        }
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Should create account, store pending outbox event, and publish to Kafka topic account.events.v1")
    void shouldCreateAccountStoreOutboxAndPublishToKafka() throws Exception {
        CreateAccountCommand command = new CreateAccountCommand(
                "Carlos Drummond de Andrade",
                "52998224725",
                LocalDate.of(1985, 10, 31),
                "carlos.drummond@example.com",
                "+5511999998888",
                "Password@123"
        );

        // Step 1: Execute use case
        CreatedAccountResult result = createAccountUseCase.createAccount(command);
        assertThat(result).isNotNull();
        assertThat(result.accountId()).isNotNull();

        // Step 2: Verify outbox record is initially pending
        List<OutboxEvent> initialEvents = outboxEventRepository.findAll();
        assertThat(initialEvents).hasSize(1);

        OutboxEvent pendingEvent = initialEvents.getFirst();
        assertThat(pendingEvent.getTopic()).isEqualTo(AccountOpenedEvent.TOPIC);
        assertThat(pendingEvent.getMessageKey()).isEqualTo(result.accountId().toString());
        assertThat(pendingEvent.getEventType()).isEqualTo(AccountOpenedEvent.EVENT_TYPE);
        assertThat(pendingEvent.getPublishedAt()).isNull();
        assertThat(pendingEvent.isPublished()).isFalse();
        assertThat(pendingEvent.getAttempts()).isEqualTo(0);

        // Step 3: Trigger OutboxPoller cycle
        int publishedCount = outboxPollerLifecycle.triggerPolling();
        assertThat(publishedCount).isEqualTo(1);

        // Step 4: Consume record from Kafka topic account.events.v1
        ConsumerRecord<String, String> receivedRecord = pollRecordForKey(consumer, result.accountId().toString(), Duration.ofSeconds(10));
        assertThat(receivedRecord).isNotNull();
        assertThat(receivedRecord.topic()).isEqualTo(AccountOpenedEvent.TOPIC);
        assertThat(receivedRecord.key()).isEqualTo(result.accountId().toString());

        JsonNode payloadJson = objectMapper.readTree(receivedRecord.value());
        assertThat(payloadJson.get("accountId").asString()).isEqualTo(result.accountId().toString());
        assertThat(payloadJson.get("customerId").asString()).isEqualTo(result.customerId().toString());
        assertThat(payloadJson.get("customerCpf").asString()).isEqualTo("52998224725");
        assertThat(payloadJson.get("customerName").asString()).isEqualTo("Carlos Drummond de Andrade");
        assertThat(payloadJson.get("branch").asString()).isEqualTo(result.branch());
        assertThat(payloadJson.get("accountNumber").asString()).isEqualTo(result.accountNumber());
        assertThat(payloadJson.get("checkDigit").asString()).isEqualTo(result.checkDigit());
        assertThat(payloadJson.get("openedAt")).isNotNull();

        // Step 5: Verify outbox record is now published in PostgreSQL
        OutboxEvent publishedEvent = outboxEventRepository.findById(pendingEvent.getId()).orElseThrow();
        assertThat(publishedEvent.isPublished()).isTrue();
        assertThat(publishedEvent.getPublishedAt()).isNotNull();
        assertThat(publishedEvent.getAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should automatically poll and publish events via OutboxPollerLifecycle virtual thread")
    void shouldAutomaticallyPublishEventsWhenLifecycleIsRunning() throws Exception {
        // Start background poller virtual thread
        outboxPollerLifecycle.start();
        assertThat(outboxPollerLifecycle.isRunning()).isTrue();
        assertThat(outboxPollerLifecycle.getPoller().getPollerThread().isVirtual()).isTrue();

        CreateAccountCommand command = new CreateAccountCommand(
                "Clarice Lispector",
                "11144477735",
                LocalDate.of(1980, 12, 10),
                "clarice.lispector@example.com",
                "+5511988887777",
                "Password@456"
        );

        CreatedAccountResult result = createAccountUseCase.createAccount(command);
        assertThat(result).isNotNull();

        // Verify that background thread polls and publishes within timeout
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    List<OutboxEvent> events = outboxEventRepository.findAll();
                    return !events.isEmpty() && events.getFirst().isPublished();
                });

        ConsumerRecord<String, String> receivedRecord = pollRecordForKey(consumer, result.accountId().toString(), Duration.ofSeconds(10));
        assertThat(receivedRecord).isNotNull();
        assertThat(receivedRecord.key()).isEqualTo(result.accountId().toString());

        // Stop lifecycle and verify status
        outboxPollerLifecycle.stop();
        assertThat(outboxPollerLifecycle.isRunning()).isFalse();
    }

    private ConsumerRecord<String, String> pollRecordForKey(
            KafkaConsumer<String, String> kafkaConsumer,
            String targetKey,
            Duration timeout
    ) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : records) {
                if (targetKey.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Timeout waiting for Kafka record with key: " + targetKey);
    }
}
