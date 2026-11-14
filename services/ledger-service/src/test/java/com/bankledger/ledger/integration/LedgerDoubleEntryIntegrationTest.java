package com.bankledger.ledger.integration;

import com.bankledger.commons.constant.BankingErrorCodes;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.ledger.adapters.out.kafka.OutboxPollerLifecycle;
import com.bankledger.ledger.application.port.out.JournalRepositoryPort;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.domain.event.JournalRejectedEvent;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.JournalStatus;
import com.bankledger.ledger.domain.model.LedgerAccount;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Ledger Double Entry End-to-End Integration Test with Testcontainers")
class LedgerDoubleEntryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("ledger_db")
            .withUsername("bank_ledger")
            .withPassword("bank_ledger_secret");

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
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("bankledger.outbox.poller.auto-startup", () -> "true");
        registry.add("bankledger.outbox.poller.interval-ms", () -> "100");
    }

    private final WebApplicationContext context;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LedgerAccountRepositoryPort ledgerAccountRepositoryPort;
    private final JournalRepositoryPort journalRepositoryPort;
    private final OutboxPollerLifecycle outboxPollerLifecycle;
    private final JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private KafkaConsumer<String, String> consumer;

    public LedgerDoubleEntryIntegrationTest(
            WebApplicationContext context,
            KafkaTemplate<String, String> kafkaTemplate,
            LedgerAccountRepositoryPort ledgerAccountRepositoryPort,
            JournalRepositoryPort journalRepositoryPort,
            OutboxPollerLifecycle outboxPollerLifecycle,
            JdbcTemplate jdbcTemplate
    ) {
        this.context = context;
        this.kafkaTemplate = kafkaTemplate;
        this.ledgerAccountRepositoryPort = ledgerAccountRepositoryPort;
        this.journalRepositoryPort = journalRepositoryPort;
        this.outboxPollerLifecycle = outboxPollerLifecycle;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        jdbcTemplate.update("DELETE FROM entry");
        jdbcTemplate.update("DELETE FROM journal");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM inbox_event");
        jdbcTemplate.update("DELETE FROM ledger_account WHERE kind = 'CUSTOMER'");
        jdbcTemplate.update("UPDATE ledger_account SET balance = 0, last_sequence = 0, version = 0 WHERE kind = 'SYSTEM'");

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(List.of(JournalRejectedEvent.TOPIC, KafkaTopics.LEDGER_JOURNAL_RESULTS_V1));

        if (!outboxPollerLifecycle.isRunning()) {
            outboxPollerLifecycle.start();
        }
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Should execute full double-entry ledger sequence: provision, deposit 500, withdraw 200, reject withdraw 500, check trial balance")
    void shouldExecuteFullDoubleEntrySequence() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // ---------------------------------------------------------------------------------
        // Step 1: Publish AccountOpened on Kafka -> verify creation of account in ledger DB
        // ---------------------------------------------------------------------------------
        String accountOpenedJson = String.format("""
                {
                  "id": "%s",
                  "source": "account-service",
                  "type": "AccountOpened",
                  "datacontenttype": "application/json",
                  "specversion": "1.0",
                  "time": "2026-11-13T10:00:00Z",
                  "data": {
                    "accountId": "%s",
                    "customerId": "%s",
                    "customerCpf": "52998224725",
                    "customerName": "Carlos Drummond de Andrade",
                    "branch": "0001",
                    "accountNumber": "100000",
                    "checkDigit": "1",
                    "openedAt": "2026-11-13T10:00:00Z"
                  }
                }
                """, UUID.randomUUID(), accountId, customerId);

        kafkaTemplate.send(KafkaTopics.ACCOUNT_EVENTS_V1, accountId.toString(), accountOpenedJson).get(5, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    Optional<LedgerAccount> account = ledgerAccountRepositoryPort.findById(accountId);
                    assertThat(account).isPresent();
                    assertThat(account.get().getBalance()).isEqualTo(0L);
                    assertThat(account.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
                    assertThat(account.get().getCode()).isEqualTo("ACC-0001-100000-1");
                });

        // ---------------------------------------------------------------------------------
        // Step 2: Post deposit R$ 500,00 -> journal POSTED, entries balanced, balance = 50,000 cents
        // ---------------------------------------------------------------------------------
        UUID txIdDeposit = UUID.randomUUID();
        String depositCommandJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "DEPOSIT",
                  "amount": 50000,
                  "currency": "BRL",
                  "destinationAccountId": "%s",
                  "terminalId": "ATM-001"
                }
                """, txIdDeposit, accountId);

        kafkaTemplate.send(KafkaTopics.LEDGER_COMMANDS_V1, txIdDeposit.toString(), depositCommandJson).get(5, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    Optional<Journal> journal = journalRepositoryPort.findByTransactionId(txIdDeposit);
                    assertThat(journal).isPresent();
                    assertThat(journal.get().getStatus()).isEqualTo(JournalStatus.POSTED);
                    assertThat(journal.get().getTotalAmount()).isEqualTo(50000L);
                    assertThat(journal.get().getEntries()).hasSize(2);

                    Optional<LedgerAccount> account = ledgerAccountRepositoryPort.findById(accountId);
                    assertThat(account).isPresent();
                    assertThat(account.get().getBalance()).isEqualTo(50000L);
                });

        // Verify balance endpoint returns 50,000 cents
        mockMvc.perform(get("/internal/v1/ledger/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50000))
                .andExpect(jsonPath("$.lastSequence").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // ---------------------------------------------------------------------------------
        // Step 3: Post withdrawal R$ 200,00 -> journal POSTED, balance = 30,000 cents
        // ---------------------------------------------------------------------------------
        UUID txIdWithdrawal1 = UUID.randomUUID();
        String withdrawal1Json = String.format("""
                {
                  "transactionId": "%s",
                  "type": "WITHDRAWAL",
                  "amount": 20000,
                  "currency": "BRL",
                  "sourceAccountId": "%s",
                  "terminalId": "ATM-001"
                }
                """, txIdWithdrawal1, accountId);

        kafkaTemplate.send(KafkaTopics.LEDGER_COMMANDS_V1, txIdWithdrawal1.toString(), withdrawal1Json).get(5, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    Optional<Journal> journal = journalRepositoryPort.findByTransactionId(txIdWithdrawal1);
                    assertThat(journal).isPresent();
                    assertThat(journal.get().getStatus()).isEqualTo(JournalStatus.POSTED);
                    assertThat(journal.get().getTotalAmount()).isEqualTo(20000L);

                    Optional<LedgerAccount> account = ledgerAccountRepositoryPort.findById(accountId);
                    assertThat(account).isPresent();
                    assertThat(account.get().getBalance()).isEqualTo(30000L);
                });

        // ---------------------------------------------------------------------------------
        // Step 4: Post withdrawal R$ 500,00 (insufficient funds) -> journal REJECTED,
        //         balance maintained at 30,000 cents, JournalRejected event in Kafka
        // ---------------------------------------------------------------------------------
        UUID txIdWithdrawal2 = UUID.randomUUID();
        String withdrawal2Json = String.format("""
                {
                  "transactionId": "%s",
                  "type": "WITHDRAWAL",
                  "amount": 50000,
                  "currency": "BRL",
                  "sourceAccountId": "%s",
                  "terminalId": "ATM-001"
                }
                """, txIdWithdrawal2, accountId);

        kafkaTemplate.send(KafkaTopics.LEDGER_COMMANDS_V1, txIdWithdrawal2.toString(), withdrawal2Json).get(5, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    Optional<Journal> journal = journalRepositoryPort.findByTransactionId(txIdWithdrawal2);
                    assertThat(journal).isPresent();
                    assertThat(journal.get().getStatus()).isEqualTo(JournalStatus.REJECTED);
                    assertThat(journal.get().getRejectCode()).isEqualTo(BankingErrorCodes.INSUFFICIENT_FUNDS);

                    Optional<LedgerAccount> account = ledgerAccountRepositoryPort.findById(accountId);
                    assertThat(account).isPresent();
                    assertThat(account.get().getBalance()).isEqualTo(30000L);
                });

        // Verify JournalRejected event is received by Kafka consumer from outbox poller
        ConsumerRecord<String, String> rejectedRecord = pollRecordForKey(consumer, txIdWithdrawal2.toString(), Duration.ofSeconds(15));
        assertThat(rejectedRecord).isNotNull();
        assertThat(rejectedRecord.topic()).isEqualTo(JournalRejectedEvent.TOPIC);
        assertThat(rejectedRecord.value()).contains(BankingErrorCodes.INSUFFICIENT_FUNDS);
        assertThat(rejectedRecord.value()).contains("REJECTED");

        // ---------------------------------------------------------------------------------
        // Step 5: Call /internal/v1/ledger/trial-balance -> confirm difference == 0
        // ---------------------------------------------------------------------------------
        mockMvc.perform(get("/internal/v1/ledger/trial-balance")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debits").value(30000))
                .andExpect(jsonPath("$.credits").value(30000))
                .andExpect(jsonPath("$.difference").value(0));

        // Also verify journal query endpoint
        mockMvc.perform(get("/internal/v1/ledger/journals/by-transaction/{transactionId}", txIdDeposit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txIdDeposit.toString()))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalAmount").value(50000))
                .andExpect(jsonPath("$.entries.length()").value(2));
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
