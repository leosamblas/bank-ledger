package com.bankledger.transaction.integration;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.commons.outbox.OutboxEventRepository;
import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.TokenClaims;
import com.bankledger.transaction.adapters.in.rest.dto.TransferRequest;
import com.bankledger.transaction.adapters.out.persistence.repository.IdempotencySpringDataRepository;
import com.bankledger.transaction.adapters.out.persistence.repository.TransactionSpringDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Transfer REST API Integration Tests with Testcontainers")
class TransferIntegrationTest {

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
    private final JwksPublicKeyProvider jwksPublicKeyProvider;
    private final TransactionSpringDataRepository transactionRepository;
    private final IdempotencySpringDataRepository idempotencyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private RsaJwtSigner rsaJwtSigner;

    public TransferIntegrationTest(
            WebApplicationContext context,
            JwksPublicKeyProvider jwksPublicKeyProvider,
            TransactionSpringDataRepository transactionRepository,
            IdempotencySpringDataRepository idempotencyRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.context = context;
        this.jwksPublicKeyProvider = jwksPublicKeyProvider;
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void setUp() {
        rsaJwtSigner = new RsaJwtSigner();
        jwksPublicKeyProvider.putKey(rsaJwtSigner.getKeyId(), rsaJwtSigner.getPublicKey());
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String generateToken(UUID customerId, UUID accountId) {
        TokenClaims claims = new TokenClaims(customerId, accountId, "0001", "123456", "ROLE_USER");
        return rsaJwtSigner.generateToken(claims);
    }

    @Test
    @DisplayName("Transfer flow: 202 Accepted + Location -> Replay -> Altered body 422 -> DB persistence")
    void testTransferFlowWithIdempotency() throws Exception {
        UUID customerA = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        String tokenA = generateToken(customerA, accountA);

        UUID idempotencyKey = UUID.randomUUID();
        TransferRequest request = new TransferRequest(accountB, 25000L, "Transferência entre contas");
        String requestBody = objectMapper.writeValueAsString(request);

        // 1. Initial Transfer Request -> 202 Accepted + Location header + PENDING body
        MvcResult initialResult = mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, idempotencyKey.toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaderConstants.LOCATION, containsString("/api/v1/transactions/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(25000))
                .andExpect(jsonPath("$.sourceAccountId").value(accountA.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(accountB.toString()))
                .andExpect(jsonPath("$.currency").value(BankingConstants.DEFAULT_CURRENCY))
                .andReturn();

        JsonNode initialJson = objectMapper.readTree(initialResult.getResponse().getContentAsString());
        String txIdStr = initialJson.path("id").asString();
        UUID txId = UUID.fromString(txIdStr);

        // Verify database persistence
        assertThat(transactionRepository.findById(txId)).isPresent();
        assertThat(outboxEventRepository.count()).isGreaterThanOrEqualTo(1);

        // 2. Replay with identical key and body -> identical 202 Accepted
        MvcResult replayResult = mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, idempotencyKey.toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaderConstants.LOCATION, "/api/v1/transactions/" + txId))
                .andExpect(jsonPath("$.id").value(txIdStr))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(25000))
                .andReturn();

        assertThat(objectMapper.readTree(replayResult.getResponse().getContentAsString()))
                .isEqualTo(objectMapper.readTree(initialResult.getResponse().getContentAsString()));

        // 3. Replay with same key but different body -> 422 Unprocessable Entity
        TransferRequest alteredRequest = new TransferRequest(accountB, 30000L, "Outro valor com mesma chave");
        String alteredBody = objectMapper.writeValueAsString(alteredRequest);

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, idempotencyKey.toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alteredBody))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"));
    }

    @Test
    @DisplayName("Payload validation: null destination, negative amount, excess amount, same source/dest -> 400 Bad Request")
    void testTransferPayloadValidation() throws Exception {
        UUID customerA = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        String tokenA = generateToken(customerA, accountA);

        // Null destinationAccountId
        String nullDest = """
                {"amount": 1000, "description": "Sem destino"}
                """;
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullDest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Negative or zero amount
        TransferRequest negativeAmount = new TransferRequest(accountB, -500L, "Negativo");
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Amount exceeding R$ 50.000,00 (5_000_000 cents)
        TransferRequest excessAmount = new TransferRequest(accountB, 5_000_001L, "Acima do limite");
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Source and destination account identical
        TransferRequest sameAccount = new TransferRequest(accountA, 1000L, "Mesma conta");
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameAccount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Security errors: missing idempotency key -> 400, missing auth -> 401, bad token -> 401")
    void testTransferSecurityErrors() throws Exception {
        UUID customerA = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        String tokenA = generateToken(customerA, accountA);

        TransferRequest validRequest = new TransferRequest(accountB, 1000L, "Válido");
        String validBody = objectMapper.writeValueAsString(validRequest);

        // Missing Idempotency-Key
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Missing Authorization
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Bad JWT token
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaderConstants.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
