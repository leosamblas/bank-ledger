package com.bankledger.ledger.integration;

import com.bankledger.commons.constant.BankingErrorCodes;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.ledger.adapters.out.kafka.OutboxPollerLifecycle;
import com.bankledger.ledger.application.port.out.JournalRepositoryPort;
import com.bankledger.ledger.application.port.out.LedgerAccountRepositoryPort;
import com.bankledger.ledger.domain.event.JournalPostedEvent;
import com.bankledger.ledger.domain.event.JournalRejectedEvent;
import com.bankledger.ledger.domain.model.AccountKind;
import com.bankledger.ledger.domain.model.AccountStatus;
import com.bankledger.ledger.domain.model.Entry;
import com.bankledger.ledger.domain.model.EntryDirection;
import com.bankledger.ledger.domain.model.Journal;
import com.bankledger.ledger.domain.model.JournalStatus;
import com.bankledger.ledger.domain.model.LedgerAccount;
import com.bankledger.ledger.domain.model.NormalBalance;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Ledger Transfer Between Accounts Integration Test with Testcontainers")
class LedgerTransferIntegrationTest {

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

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LedgerAccountRepositoryPort ledgerAccountRepositoryPort;
    private final JournalRepositoryPort journalRepositoryPort;
    private final OutboxPollerLifecycle outboxPollerLifecycle;
    private final JdbcTemplate jdbcTemplate;

    private KafkaConsumer<String, String> consumer;

    public LedgerTransferIntegrationTest(
            KafkaTemplate<String, String> kafkaTemplate,
            LedgerAccountRepositoryPort ledgerAccountRepositoryPort,
            JournalRepositoryPort journalRepositoryPort,
            OutboxPollerLifecycle outboxPollerLifecycle,
            JdbcTemplate jdbcTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.ledgerAccountRepositoryPort = ledgerAccountRepositoryPort;
        this.journalRepositoryPort = journalRepositoryPort;
        this.outboxPollerLifecycle = outboxPollerLifecycle;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM entry");
        jdbcTemplate.update("DELETE FROM journal");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM inbox_event");
        jdbcTemplate.update("DELETE FROM ledger_account WHERE kind = 'CUSTOMER'");
        jdbcTemplate.update("UPDATE ledger_account SET balance = 0, last_sequence = 0, version = 0 WHERE kind = 'SYSTEM'");

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "transfer-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(List.of(JournalRejectedEvent.TOPIC, KafkaTopics.LEDGER_JOURNAL_RESULTS_V1, KafkaTopics.LEDGER_ENTRIES_V1));

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

    private LedgerAccount createCustomerAccount(UUID accountId, String code, long initialBalance) {
        Instant now = Instant.now();
        LedgerAccount account = new LedgerAccount(
                accountId,
                code,
                AccountKind.CUSTOMER,
                NormalBalance.CREDIT,
                "BRL",
                initialBalance,
                0L,
                AccountStatus.ACTIVE,
                now,
                now,
                0L
        );
        return ledgerAccountRepositoryPort.save(account);
    }

    @Test
    @DisplayName("Should execute transfer successfully debiting source and crediting destination in ACID transaction")
    void shouldExecuteTransferSuccessfully() throws Exception {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        createCustomerAccount(accountA, "ACC-0001-111111-1", 50000L);
        createCustomerAccount(accountB, "ACC-0001-222222-2", 10000L);

        UUID txIdTransfer = UUID.randomUUID();
        String transferCommandJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "TRANSFER",
                  "amount": 20000,
                  "currency": "BRL",
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "terminalId": "API"
                }
                """, txIdTransfer, accountA, accountB);

        kafkaTemplate.send(KafkaTopics.LEDGER_COMMANDS_V1, txIdTransfer.toString(), transferCommandJson)
                .get(5, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    Optional<Journal> journal = journalRepositoryPort.findByTransactionId(txIdTransfer);
                    assertThat(journal).isPresent();
                    assertThat(journal.get().getStatus()).isEqualTo(JournalStatus.POSTED);
                    assertThat(journal.get().getType()).isEqualTo("TRANSFER");
                    assertThat(journal.get().getTotalAmount()).isEqualTo(20000L);
                    assertThat(journal.get().getEntries()).hasSize(2);

                    List<Entry> entries = journal.get().getEntries();
                    Entry debitEntry = entries.stream()
                            .filter(e -> e.direction() == EntryDirection.DEBIT)
                            .findFirst()
                            .orElseThrow();
                    Entry creditEntry = entries.stream()
                            .filter(e -> e.direction() == EntryDirection.CREDIT)
                            .findFirst()
                            .orElseThrow();

                    assertThat(debitEntry.ledgerAccountId()).isEqualTo(accountA);
                    assertThat(debitEntry.amount()).isEqualTo(20000L);
                    assertThat(debitEntry.balanceAfter()).isEqualTo(30000L);

                    assertThat(creditEntry.ledgerAccountId()).isEqualTo(accountB);
                    assertThat(creditEntry.amount()).isEqualTo(20000L);
                    assertThat(creditEntry.balanceAfter()).isEqualTo(30000L);

                    Optional<LedgerAccount> updatedA = ledgerAccountRepositoryPort.findById(accountA);
                    assertThat(updatedA).isPresent();
                    assertThat(updatedA.get().getBalance()).isEqualTo(30000L);

                    Optional<LedgerAccount> updatedB = ledgerAccountRepositoryPort.findById(accountB);
                    assertThat(updatedB).isPresent();
                    assertThat(updatedB.get().getBalance()).isEqualTo(30000L);
                });

        // Verify JournalPosted event in Kafka
        ConsumerRecord<String, String> postedRecord = pollRecordForKey(consumer, txIdTransfer.toString(), Duration.ofSeconds(15));
        assertThat(postedRecord).isNotNull();
        assertThat(postedRecord.topic()).isEqualTo(JournalPostedEvent.TOPIC);
        assertThat(postedRecord.value()).contains("POSTED");
        assertThat(postedRecord.value()).contains("TRANSFER");
    }

    @Test
    @DisplayName("Should reject transfer when source account has insufficient funds without altering balances")
    void shouldRejectTransferWhenInsufficientFunds() throws Exception {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        createCustomerAccount(accountA, "ACC-0001-333333-3", 10000L);
        createCustomerAccount(accountB, "ACC-0001-444444-4", 5000L);

        UUID txIdTransfer = UUID.randomUUID();
        String transferCommandJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "TRANSFER",
                  "amount": 25000,
                  "currency": "BRL",
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "terminalId": "API"
                }
                """, txIdTransfer, accountA, accountB);

        kafkaTemplate.send(KafkaTopics.LEDGER_COMMANDS_V1, txIdTransfer.toString(), transferCommandJson)
                .get(5, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    Optional<Journal> journal = journalRepositoryPort.findByTransactionId(txIdTransfer);
                    assertThat(journal).isPresent();
                    assertThat(journal.get().getStatus()).isEqualTo(JournalStatus.REJECTED);
                    assertThat(journal.get().getType()).isEqualTo("TRANSFER");
                    assertThat(journal.get().getRejectCode()).isEqualTo(BankingErrorCodes.INSUFFICIENT_FUNDS);
                    assertThat(journal.get().getEntries()).isEmpty();

                    Optional<LedgerAccount> accA = ledgerAccountRepositoryPort.findById(accountA);
                    assertThat(accA).isPresent();
                    assertThat(accA.get().getBalance()).isEqualTo(10000L);

                    Optional<LedgerAccount> accB = ledgerAccountRepositoryPort.findById(accountB);
                    assertThat(accB).isPresent();
                    assertThat(accB.get().getBalance()).isEqualTo(5000L);
                });

        // Verify JournalRejected event in Kafka
        ConsumerRecord<String, String> rejectedRecord = pollRecordForKey(consumer, txIdTransfer.toString(), Duration.ofSeconds(15));
        assertThat(rejectedRecord).isNotNull();
        assertThat(rejectedRecord.topic()).isEqualTo(JournalRejectedEvent.TOPIC);
        assertThat(rejectedRecord.value()).contains(BankingErrorCodes.INSUFFICIENT_FUNDS);
        assertThat(rejectedRecord.value()).contains("REJECTED");
    }

    @Test
    @DisplayName("Should execute transfer regardless of UUID order between source and destination (anti-deadlock ADR 0002)")
    void shouldExecuteTransferWithInvertedUuidOrdering() throws Exception {
        UUID id1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID id2 = UUID.fromString("99999999-9999-9999-9999-999999999999");

        // Transfer from larger UUID (id2) to smaller UUID (id1)
        createCustomerAccount(id1, "ACC-0001-ORDER-1", 10000L);
        createCustomerAccount(id2, "ACC-0001-ORDER-2", 30000L);

        UUID txId = UUID.randomUUID();
        String transferCommandJson = String.format("""
                {
                  "transactionId": "%s",
                  "type": "TRANSFER",
                  "amount": 10000,
                  "currency": "BRL",
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "terminalId": "API"
                }
                """, txId, id2, id1);

        kafkaTemplate.send(KafkaTopics.LEDGER_COMMANDS_V1, txId.toString(), transferCommandJson)
                .get(5, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    Optional<Journal> journal = journalRepositoryPort.findByTransactionId(txId);
                    assertThat(journal).isPresent();
                    assertThat(journal.get().getStatus()).isEqualTo(JournalStatus.POSTED);

                    Optional<LedgerAccount> updatedSource = ledgerAccountRepositoryPort.findById(id2);
                    assertThat(updatedSource).isPresent();
                    assertThat(updatedSource.get().getBalance()).isEqualTo(20000L);

                    Optional<LedgerAccount> updatedDest = ledgerAccountRepositoryPort.findById(id1);
                    assertThat(updatedDest).isPresent();
                    assertThat(updatedDest.get().getBalance()).isEqualTo(20000L);
                });
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
