package com.bankledger.query.integration;

import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.events.v1.account.AccountOpenedPayload;
import com.bankledger.query.adapters.out.persistence.AccountBalanceEntity;
import com.bankledger.query.adapters.out.persistence.AccountBalanceRepository;
import com.bankledger.query.adapters.out.persistence.StatementEntryEntity;
import com.bankledger.query.adapters.out.persistence.StatementEntryRepository;
import com.bankledger.query.config.JwtTokenService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Query Service CQRS Integration Tests with Testcontainers")
class QueryServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("query_db")
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
    }

    private final WebApplicationContext context;
    private final AccountBalanceRepository accountBalanceRepository;
    private final StatementEntryRepository statementEntryRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final JwtTokenService jwtTokenService;

    private MockMvc mockMvc;

    public QueryServiceIntegrationTest(
            WebApplicationContext context,
            AccountBalanceRepository accountBalanceRepository,
            StatementEntryRepository statementEntryRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            JwtTokenService jwtTokenService
    ) {
        this.context = context;
        this.accountBalanceRepository = accountBalanceRepository;
        this.statementEntryRepository = statementEntryRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.jwtTokenService = jwtTokenService;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Garantia de saldo zero: consulta direta de conta recém-criada sem eventos retorna strictly 0 centavos")
    void testDirectQueryUnseenAccountReturnsZeroBalance() throws Exception {
        UUID newAccountId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/queries/accounts/{id}/balance", newAccountId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(newAccountId.toString()))
                .andExpect(jsonPath("$.availableCents").value(0))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.blockedCents").value(0))
                .andExpect(jsonPath("$.dailyLimitCents").value(5000000))
                .andExpect(jsonPath("$.usedTodayCents").value(0))
                .andExpect(jsonPath("$.monthlyVariationPercent").value(0.0))
                .andExpect(jsonPath("$.asOfSequence").value(0));

        // Verifica que o registro agora está persistido no banco com saldo zero
        Optional<AccountBalanceEntity> persisted = accountBalanceRepository.findById(newAccountId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getBalance()).isEqualTo(0L);
        assertThat(persisted.get().getLastSequence()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Criação de saldo zero ao receber AccountOpened no tópico account.events.v1")
    void testAccountOpenedInitializesZeroBalance() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        AccountOpenedPayload payload = new AccountOpenedPayload(
                accountId,
                customerId,
                "123.456.789-00",
                "Maria Silva",
                "0001",
                "98765432",
                "1",
                Instant.now()
        );

        String json = objectMapper.writeValueAsString(payload);
        kafkaTemplate.send(KafkaTopics.ACCOUNT_EVENTS_V1, accountId.toString(), json).get(5, TimeUnit.SECONDS);

        // Await projection execution
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Optional<AccountBalanceEntity> balanceOpt = accountBalanceRepository.findById(accountId);
                    assertThat(balanceOpt).isPresent();
                    assertThat(balanceOpt.get().getBalance()).isEqualTo(0L);
                    assertThat(balanceOpt.get().getLastSequence()).isEqualTo(0L);
                    assertThat(balanceOpt.get().getCurrency()).isEqualTo("BRL");
                });

        // Consulta via REST endpoint
        mockMvc.perform(get("/api/v1/queries/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.availableCents").value(0))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.asOfSequence").value(0));
    }

    @Test
    @DisplayName("Recebimento de EntryPosted com crédito de 10.000 centavos -> atualiza saldo e extrato")
    void testEntryPostedUpdatesBalanceAndStatement() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        // 1. Envia primeiro AccountOpened para inicializar com zero
        AccountOpenedPayload accountPayload = new AccountOpenedPayload(
                accountId,
                UUID.randomUUID(),
                "999.888.777-66",
                "João Santos",
                "0001",
                "12345678",
                "9",
                Instant.now()
        );
        kafkaTemplate.send(KafkaTopics.ACCOUNT_EVENTS_V1, accountId.toString(), objectMapper.writeValueAsString(accountPayload))
                .get(5, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(accountBalanceRepository.findById(accountId)).isPresent());

        // 2. Publica EntryPosted no tópico ledger.entries.v1 com crédito de R$ 100,00 (10.000 centavos)
        String entryPostedJson = String.format("""
                {
                  "entryId": "%s",
                  "journalId": "%s",
                  "transactionId": "%s",
                  "accountId": "%s",
                  "direction": "CREDIT",
                  "amount": 10000,
                  "balanceAfter": 10000,
                  "sequence": 1,
                  "category": "DEPOSIT",
                  "type": "DEPOSIT",
                  "description": "Depósito em dinheiro",
                  "postedAt": "%s"
                }
                """,
                entryId,
                journalId,
                transactionId,
                accountId,
                Instant.now().toString()
        );

        kafkaTemplate.send(KafkaTopics.LEDGER_ENTRIES_V1, accountId.toString(), entryPostedJson)
                .get(5, TimeUnit.SECONDS);

        // 3. Await balance update in repository
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Optional<AccountBalanceEntity> balanceOpt = accountBalanceRepository.findById(accountId);
                    assertThat(balanceOpt).isPresent();
                    assertThat(balanceOpt.get().getBalance()).isEqualTo(10000L);
                    assertThat(balanceOpt.get().getLastSequence()).isEqualTo(1L);

                    List<StatementEntryEntity> statements = statementEntryRepository.findByAccountIdOrderBySequenceDesc(accountId);
                    assertThat(statements).hasSize(1);
                    StatementEntryEntity entry = statements.get(0);
                    assertThat(entry.getAmount()).isEqualTo(10000L);
                    assertThat(entry.getBalanceAfter()).isEqualTo(10000L);
                    assertThat(entry.getDirection()).isEqualTo("CREDIT");
                    assertThat(entry.getSequence()).isEqualTo(1L);
                });

        // 4. Valida endpoint REST de saldo
        mockMvc.perform(get("/api/v1/queries/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.availableCents").value(10000))
                .andExpect(jsonPath("$.balance").value(10000))
                .andExpect(jsonPath("$.asOfSequence").value(1));

        // 5. Valida endpoint REST de extrato
        mockMvc.perform(get("/api/v1/queries/accounts/{id}/statement", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sequence").value(1))
                .andExpect(jsonPath("$.items[0].transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.items[0].journalId").value(journalId.toString()))
                .andExpect(jsonPath("$.items[0].nature").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].amountCents").value(10000))
                .andExpect(jsonPath("$.items[0].balanceAfterCents").value(10000))
                .andExpect(jsonPath("$.items[0].description").value("Depósito em dinheiro"));
    }
}
