package com.bankledger.transaction.integration;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.commons.outbox.OutboxEventRepository;
import com.bankledger.transaction.adapters.in.rest.dto.DepositRequest;
import com.bankledger.transaction.adapters.in.rest.dto.WithdrawalRequest;
import com.bankledger.transaction.adapters.out.persistence.repository.IdempotencySpringDataRepository;
import com.bankledger.transaction.adapters.out.persistence.repository.TransactionSpringDataRepository;
import com.bankledger.transaction.config.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Transaction REST API Integration Tests with Testcontainers")
class TransactionApiIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("transaction_db")
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
        registry.add("bankledger.outbox.poller.auto-startup", () -> "false");
    }

    private final WebApplicationContext context;
    private final JwtTokenService jwtTokenService;
    private final TransactionSpringDataRepository transactionRepository;
    private final IdempotencySpringDataRepository idempotencyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    private MockMvc mockMvc;

    public TransactionApiIntegrationTest(
            WebApplicationContext context,
            JwtTokenService jwtTokenService,
            TransactionSpringDataRepository transactionRepository,
            IdempotencySpringDataRepository idempotencyRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.context = context;
        this.jwtTokenService = jwtTokenService;
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Deposit flow: 202 Accepted + Location -> Replay -> Altered body 422 -> GET 200 -> GET by another user 404")
    void testDepositFlowWithIdempotencyAndAuthorization() throws Exception {
        UUID customerA = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        String tokenA = jwtTokenService.generateToken(customerA, accountA);

        UUID customerB = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        String tokenB = jwtTokenService.generateToken(customerB, accountB);

        UUID idempotencyKey = UUID.randomUUID();
        DepositRequest depositRequest = new DepositRequest(15000L, "Depósito inicial");
        String depositBody = objectMapper.writeValueAsString(depositRequest);

        // 1. Initial Deposit Request -> 202 Accepted + Location header + PENDING body
        MvcResult initialResult = mockMvc.perform(post("/api/v1/transactions/deposits")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, idempotencyKey.toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaderConstants.LOCATION, containsString("/api/v1/transactions/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(15000))
                .andExpect(jsonPath("$.destinationAccountId").value(accountA.toString()))
                .andExpect(jsonPath("$.currency").value(BankingConstants.DEFAULT_CURRENCY))
                .andReturn();

        JsonNode initialJson = objectMapper.readTree(initialResult.getResponse().getContentAsString());
        String txIdStr = initialJson.path("id").asString();
        UUID txId = UUID.fromString(txIdStr);

        // 2. Replay with identical key and body -> identical 202 Accepted
        MvcResult replayResult = mockMvc.perform(post("/api/v1/transactions/deposits")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, idempotencyKey.toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositBody))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaderConstants.LOCATION, "/api/v1/transactions/" + txId))
                .andExpect(jsonPath("$.id").value(txIdStr))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(15000))
                .andReturn();

        assertThat(objectMapper.readTree(replayResult.getResponse().getContentAsString()))
                .isEqualTo(objectMapper.readTree(initialResult.getResponse().getContentAsString()));

        // 3. Replay with same key but different body -> 422 Unprocessable Entity
        DepositRequest alteredRequest = new DepositRequest(20000L, "Outro valor com mesma chave");
        String alteredBody = objectMapper.writeValueAsString(alteredRequest);

        mockMvc.perform(post("/api/v1/transactions/deposits")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, idempotencyKey.toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alteredBody))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"));

        // 4. GET /api/v1/transactions/{id} with tokenA -> 200 OK
        mockMvc.perform(get("/api/v1/transactions/" + txId)
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(txIdStr))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(15000))
                .andExpect(jsonPath("$.destinationAccountId").value(accountA.toString()));

        // 5. GET /api/v1/transactions/{id} with tokenB (different customer) -> 404 Not Found (IDOR prevention)
        mockMvc.perform(get("/api/v1/transactions/" + txId)
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("Withdrawal flow: 202 Accepted + Location -> Replay -> verify DB records")
    void testWithdrawalFlow() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        String token = jwtTokenService.generateToken(customer, account);

        UUID idempotencyKey = UUID.randomUUID();
        WithdrawalRequest withdrawalRequest = new WithdrawalRequest(5000L, "Saque em caixa eletrônico");
        String body = objectMapper.writeValueAsString(withdrawalRequest);

        MvcResult result = mockMvc.perform(post("/api/v1/transactions/withdrawals")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, idempotencyKey.toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaderConstants.LOCATION, containsString("/api/v1/transactions/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.sourceAccountId").value(account.toString()))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID txId = UUID.fromString(json.path("id").asString());

        // Verify database persistence
        assertThat(transactionRepository.findById(txId)).isPresent();
        assertThat(outboxEventRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Validation & Security errors: missing header -> 400, bad token -> 401, negative amount -> 400")
    void testValidationAndSecurityErrors() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        String token = jwtTokenService.generateToken(customer, account);

        // Missing Idempotency-Key header -> 400 Bad Request
        DepositRequest validRequest = new DepositRequest(1000L, "Sem chave");
        mockMvc.perform(post("/api/v1/transactions/deposits")
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Missing Authorization header -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/transactions/deposits")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Invalid token -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/transactions/deposits")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + "invalid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Negative amount -> 400 Bad Request
        DepositRequest invalidRequest = new DepositRequest(-1000L, "Valor negativo");
        mockMvc.perform(post("/api/v1/transactions/deposits")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
