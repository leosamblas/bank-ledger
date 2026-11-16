package com.bankledger.account.integration;

import com.bankledger.account.adapters.out.kafka.OutboxPollerLifecycle;
import com.bankledger.account.adapters.out.persistence.repository.AccountSpringDataRepository;
import com.bankledger.account.adapters.out.persistence.repository.CustomerSpringDataRepository;
import com.bankledger.account.application.port.in.CreateAccountCommand;
import com.bankledger.account.application.port.in.CreateAccountUseCase;
import com.bankledger.account.application.port.in.CreatedAccountResult;
import com.bankledger.account.domain.event.AccountOpenedEvent;
import com.bankledger.commons.constant.KafkaTopics;
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
import java.util.*;

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
        consumer.subscribe(List.of(AccountOpenedEvent.TOPIC, KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1));
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
    @DisplayName("Should create account, store pending outbox events, and publish to Kafka topics account.events.v1 and auth.credentials-registration.v1")
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

        // Step 2: Verify outbox records are initially pending (both AccountOpened and RegisterCredentials)
        List<OutboxEvent> initialEvents = outboxEventRepository.findAll();
        assertThat(initialEvents).hasSize(2);

        OutboxEvent pendingAccountEvent = initialEvents.stream()
                .filter(e -> "Account".equals(e.getAggregateType()))
                .findFirst()
                .orElseThrow();
        assertThat(pendingAccountEvent.getTopic()).isEqualTo(AccountOpenedEvent.TOPIC);
        assertThat(pendingAccountEvent.getMessageKey()).isEqualTo(result.accountId().toString());
        assertThat(pendingAccountEvent.getEventType()).isEqualTo(AccountOpenedEvent.EVENT_TYPE);
        assertThat(pendingAccountEvent.getPublishedAt()).isNull();
        assertThat(pendingAccountEvent.isPublished()).isFalse();
        assertThat(pendingAccountEvent.getAttempts()).isEqualTo(0);

        OutboxEvent pendingCredentialsEvent = initialEvents.stream()
                .filter(e -> "AuthCredentials".equals(e.getAggregateType()))
                .findFirst()
                .orElseThrow();
        assertThat(pendingCredentialsEvent.getTopic()).isEqualTo(KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1);
        assertThat(pendingCredentialsEvent.getMessageKey()).isEqualTo(result.customerId().toString());
        assertThat(pendingCredentialsEvent.getEventType()).isEqualTo("RegisterCredentials");
        assertThat(pendingCredentialsEvent.getPublishedAt()).isNull();
        assertThat(pendingCredentialsEvent.isPublished()).isFalse();
        assertThat(pendingCredentialsEvent.getAttempts()).isEqualTo(0);

        // Step 3: Trigger OutboxPoller cycle
        int publishedCount = outboxPollerLifecycle.triggerPolling();
        assertThat(publishedCount).isEqualTo(2);

        // Step 4: Consume records from Kafka topics
        Map<String, ConsumerRecord<String, String>> recordsByKey = pollRecordsForKeys(
                consumer,
                Set.of(result.accountId().toString(), result.customerId().toString()),
                Duration.ofSeconds(10)
        );

        ConsumerRecord<String, String> accountRecord = recordsByKey.get(result.accountId().toString());
        assertThat(accountRecord).isNotNull();
        assertThat(accountRecord.topic()).isEqualTo(AccountOpenedEvent.TOPIC);
        assertThat(accountRecord.key()).isEqualTo(result.accountId().toString());

        JsonNode payloadJson = objectMapper.readTree(accountRecord.value());
        assertThat(payloadJson.get("accountId").asString()).isEqualTo(result.accountId().toString());
        assertThat(payloadJson.get("customerId").asString()).isEqualTo(result.customerId().toString());
        assertThat(payloadJson.get("customerCpf").asString()).isEqualTo("52998224725");
        assertThat(payloadJson.get("customerName").asString()).isEqualTo("Carlos Drummond de Andrade");
        assertThat(payloadJson.get("branch").asString()).isEqualTo(result.branch());
        assertThat(payloadJson.get("accountNumber").asString()).isEqualTo(result.accountNumber());
        assertThat(payloadJson.get("checkDigit").asString()).isEqualTo(result.checkDigit());
        assertThat(payloadJson.get("openedAt")).isNotNull();

        ConsumerRecord<String, String> credentialsRecord = recordsByKey.get(result.customerId().toString());
        assertThat(credentialsRecord).isNotNull();
        assertThat(credentialsRecord.topic()).isEqualTo(KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1);
        assertThat(credentialsRecord.key()).isEqualTo(result.customerId().toString());

        JsonNode credentialsPayloadJson = objectMapper.readTree(credentialsRecord.value());
        assertThat(credentialsPayloadJson.get("customerId").asString()).isEqualTo(result.customerId().toString());
        assertThat(credentialsPayloadJson.get("cpf").asString()).isEqualTo("52998224725");
        assertThat(credentialsPayloadJson.get("password").asString()).isEqualTo("Password@123");
        assertThat(credentialsPayloadJson.get("accountId").asString()).isEqualTo(result.accountId().toString());
        assertThat(credentialsPayloadJson.get("branch").asString()).isEqualTo(result.branch());
        assertThat(credentialsPayloadJson.get("accountNumber").asString()).isEqualTo(result.accountNumber());
        assertThat(credentialsPayloadJson.get("fullName").asString()).isEqualTo("Carlos Drummond de Andrade");
        assertThat(credentialsPayloadJson.get("email").asString()).isEqualTo("carlos.drummond@example.com");

        // Step 5: Verify outbox records are now published in PostgreSQL
        OutboxEvent publishedAccountEvent = outboxEventRepository.findById(pendingAccountEvent.getId()).orElseThrow();
        assertThat(publishedAccountEvent.isPublished()).isTrue();
        assertThat(publishedAccountEvent.getPublishedAt()).isNotNull();
        assertThat(publishedAccountEvent.getAttempts()).isEqualTo(0);

        OutboxEvent publishedCredentialsEvent = outboxEventRepository.findById(pendingCredentialsEvent.getId()).orElseThrow();
        assertThat(publishedCredentialsEvent.isPublished()).isTrue();
        assertThat(publishedCredentialsEvent.getPublishedAt()).isNotNull();
        assertThat(publishedCredentialsEvent.getAttempts()).isEqualTo(0);
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

        // Verify that background thread polls and publishes both events within timeout
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    List<OutboxEvent> events = outboxEventRepository.findAll();
                    return events.size() == 2 && events.stream().allMatch(OutboxEvent::isPublished);
                });

        Map<String, ConsumerRecord<String, String>> recordsByKey = pollRecordsForKeys(
                consumer,
                Set.of(result.accountId().toString(), result.customerId().toString()),
                Duration.ofSeconds(10)
        );

        ConsumerRecord<String, String> accountRecord = recordsByKey.get(result.accountId().toString());
        assertThat(accountRecord).isNotNull();
        assertThat(accountRecord.key()).isEqualTo(result.accountId().toString());

        ConsumerRecord<String, String> credentialsRecord = recordsByKey.get(result.customerId().toString());
        assertThat(credentialsRecord).isNotNull();
        assertThat(credentialsRecord.key()).isEqualTo(result.customerId().toString());

        // Stop lifecycle and verify status
        outboxPollerLifecycle.stop();
        assertThat(outboxPollerLifecycle.isRunning()).isFalse();
    }

    private Map<String, ConsumerRecord<String, String>> pollRecordsForKeys(
            KafkaConsumer<String, String> kafkaConsumer,
            Set<String> targetKeys,
            Duration timeout
    ) {
        Map<String, ConsumerRecord<String, String>> found = new HashMap<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline && found.size() < targetKeys.size()) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : records) {
                if (targetKeys.contains(record.key())) {
                    found.put(record.key(), record);
                }
            }
        }
        if (found.size() < targetKeys.size()) {
            throw new AssertionError("Timeout waiting for Kafka records with keys: " + targetKeys + ", found: " + found.keySet());
        }
        return found;
    }
}
