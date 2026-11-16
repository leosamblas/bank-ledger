package com.bankledger.query.integration;

import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.TokenClaims;
import com.bankledger.query.adapters.out.persistence.AccountBalanceEntity;
import com.bankledger.query.adapters.out.persistence.AccountBalanceRepository;
import com.bankledger.query.adapters.out.persistence.StatementEntryEntity;
import com.bankledger.query.adapters.out.persistence.StatementEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Query Service Security & BOLA/IDOR Integration Tests")
class QuerySecurityIntegrationTest {

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
    private final JwksPublicKeyProvider jwksPublicKeyProvider;
    private final AccountBalanceRepository accountBalanceRepository;
    private final StatementEntryRepository statementEntryRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private MockMvc mockMvc;
    private RsaJwtSigner rsaJwtSigner;

    public QuerySecurityIntegrationTest(
            WebApplicationContext context,
            JwksPublicKeyProvider jwksPublicKeyProvider,
            AccountBalanceRepository accountBalanceRepository,
            StatementEntryRepository statementEntryRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.context = context;
        this.jwksPublicKeyProvider = jwksPublicKeyProvider;
        this.accountBalanceRepository = accountBalanceRepository;
        this.statementEntryRepository = statementEntryRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        rsaJwtSigner = new RsaJwtSigner(objectMapper, clock, "test-query-key");
        jwksPublicKeyProvider.putKey(rsaJwtSigner.getKeyId(), rsaJwtSigner.getPublicKey());
    }

    private String createToken(UUID customerId, UUID accountId, String role) {
        TokenClaims claims = new TokenClaims(customerId, accountId, "12345678900", role, "0001", "12345678");
        return rsaJwtSigner.generateToken(claims, Duration.ofHours(1));
    }

    @Nested
    @DisplayName("OWASP BOLA / IDOR Protection Tests")
    class BolaProtectionTests {

        @Test
        @DisplayName("User A querying Account B balance receives HTTP 403 Forbidden with RFC 7807 ProblemDetail")
        void userAQueryingUserBBalanceReceives403Forbidden() throws Exception {
            UUID accountA = UUID.randomUUID();
            UUID accountB = UUID.randomUUID();
            UUID customerA = UUID.randomUUID();

            String tokenA = createToken(customerA, accountA, "ROLE_USER");

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/balance", accountB)
                            .header(HttpHeaderConstants.AUTHORIZATION, "Bearer " + tokenA)
                            .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Content-Type", equalTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.detail").value("Access denied: user is not the owner of this account"));
        }

        @Test
        @DisplayName("User A querying Account B statement receives HTTP 403 Forbidden with RFC 7807 ProblemDetail")
        void userAQueryingUserBStatementReceives403Forbidden() throws Exception {
            UUID accountA = UUID.randomUUID();
            UUID accountB = UUID.randomUUID();
            UUID customerA = UUID.randomUUID();

            String tokenA = createToken(customerA, accountA, "ROLE_USER");

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/statement", accountB)
                            .header(HttpHeaderConstants.AUTHORIZATION, "Bearer " + tokenA)
                            .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Content-Type", equalTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.detail").value("Access denied: user is not the owner of this account"));
        }

        @Test
        @DisplayName("User A querying own account balance receives HTTP 200 OK")
        void userAQueryingOwnAccountBalanceReceives200Ok() throws Exception {
            UUID accountA = UUID.randomUUID();
            UUID customerA = UUID.randomUUID();

            String tokenA = createToken(customerA, accountA, "ROLE_USER");

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/balance", accountA)
                            .header(HttpHeaderConstants.AUTHORIZATION, "Bearer " + tokenA)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(accountA.toString()))
                    .andExpect(jsonPath("$.balance").value(0));
        }

        @Test
        @DisplayName("User A querying own account statement receives HTTP 200 OK")
        void userAQueryingOwnAccountStatementReceives200Ok() throws Exception {
            UUID accountA = UUID.randomUUID();
            UUID customerA = UUID.randomUUID();

            String tokenA = createToken(customerA, accountA, "ROLE_USER");

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/statement", accountA)
                            .header(HttpHeaderConstants.AUTHORIZATION, "Bearer " + tokenA)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("Admin querying another user's account balance receives HTTP 200 OK")
        void adminQueryingUserBBalanceReceives200Ok() throws Exception {
            UUID adminAccountId = UUID.randomUUID();
            UUID adminCustomerId = UUID.randomUUID();
            UUID targetAccountId = UUID.randomUUID();

            String adminToken = createToken(adminCustomerId, adminAccountId, "ROLE_ADMIN");

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/balance", targetAccountId)
                            .header(HttpHeaderConstants.AUTHORIZATION, "Bearer " + adminToken)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(targetAccountId.toString()));
        }

        @Test
        @DisplayName("Admin querying another user's account statement receives HTTP 200 OK")
        void adminQueryingUserBStatementReceives200Ok() throws Exception {
            UUID adminAccountId = UUID.randomUUID();
            UUID adminCustomerId = UUID.randomUUID();
            UUID targetAccountId = UUID.randomUUID();

            String adminToken = createToken(adminCustomerId, adminAccountId, "ROLE_ADMIN");

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/statement", targetAccountId)
                            .header(HttpHeaderConstants.AUTHORIZATION, "Bearer " + adminToken)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray());
        }
    }

    @Nested
    @DisplayName("Authentication & Token Integrity Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Unauthenticated request to balance endpoint returns HTTP 401 Unauthorized")
        void unauthenticatedRequestToBalanceReturns401() throws Exception {
            UUID accountId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/balance", accountId)
                            .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated request to statement endpoint returns HTTP 401 Unauthorized")
        void unauthenticatedRequestToStatementReturns401() throws Exception {
            UUID accountId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/statement", accountId)
                            .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token missing accountId claim returns HTTP 401 Unauthorized")
        void tokenMissingAccountIdReturns401() throws Exception {
            UUID customerId = UUID.randomUUID();
            UUID targetAccountId = UUID.randomUUID();

            // Create token with null accountId
            String tokenWithoutAccount = rsaJwtSigner.generateToken(Map.of(
                    "sub", customerId.toString(),
                    "customerId", customerId.toString(),
                    "role", "ROLE_USER"
            ), Duration.ofHours(1));

            mockMvc.perform(get("/api/v1/queries/accounts/{accountId}/balance", targetAccountId)
                            .header(HttpHeaderConstants.AUTHORIZATION, "Bearer " + tokenWithoutAccount)
                            .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }
}
