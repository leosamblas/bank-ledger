package com.bankledger.ledger.integration;

import com.bankledger.commons.security.SecurityConstants;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Internal Ledger Security Integration Tests")
class InternalLedgerSecurityTest {

    private static final String VALID_SERVICE_KEY = "bankledger-internal-secret-ledger-key-2026";
    private static final String INVALID_SERVICE_KEY = "invalid-secret-key-1234";

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
        registry.add("bankledger.outbox.poller.auto-startup", () -> "false");
        registry.add("app.security.internal-service-key", () -> VALID_SERVICE_KEY);
    }

    private final WebApplicationContext context;
    private MockMvc mockMvc;

    public InternalLedgerSecurityTest(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/trial-balance without header returns 401 with RFC 7807 ProblemDetail")
    void shouldReturn401WhenRequestingTrialBalanceWithoutHeader() throws Exception {
        mockMvc.perform(get("/internal/v1/ledger/trial-balance")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Unauthorized: missing or invalid internal service key"))
                .andExpect(jsonPath("$.instance").value("/internal/v1/ledger/trial-balance"));
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/trial-balance with invalid header returns 401 with RFC 7807 ProblemDetail")
    void shouldReturn401WhenRequestingTrialBalanceWithInvalidHeader() throws Exception {
        mockMvc.perform(get("/internal/v1/ledger/trial-balance")
                        .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, INVALID_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Unauthorized: missing or invalid internal service key"))
                .andExpect(jsonPath("$.instance").value("/internal/v1/ledger/trial-balance"));
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/trial-balance with valid X-Internal-Service-Key returns 200 OK")
    void shouldReturn200WhenRequestingTrialBalanceWithValidHeader() throws Exception {
        mockMvc.perform(get("/internal/v1/ledger/trial-balance")
                        .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debits").isNumber())
                .andExpect(jsonPath("$.credits").isNumber())
                .andExpect(jsonPath("$.difference").isNumber());
    }

    @Test
    @DisplayName("GET /actuator/health is accessible without X-Internal-Service-Key")
    void shouldPermitActuatorEndpointsAnonymously() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/accounts/{id}/balance without header returns 401 with RFC 7807 ProblemDetail")
    void shouldReturn401WhenRequestingAccountBalanceWithoutHeader() throws Exception {
        UUID accountId = UUID.randomUUID();
        mockMvc.perform(get("/internal/v1/ledger/accounts/{id}/balance", accountId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Unauthorized: missing or invalid internal service key"))
                .andExpect(jsonPath("$.instance").value("/internal/v1/ledger/accounts/" + accountId + "/balance"));
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/journals/by-transaction/{id} without header returns 401 with RFC 7807 ProblemDetail")
    void shouldReturn401WhenRequestingJournalWithoutHeader() throws Exception {
        UUID txId = UUID.randomUUID();
        mockMvc.perform(get("/internal/v1/ledger/journals/by-transaction/{transactionId}", txId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Unauthorized: missing or invalid internal service key"))
                .andExpect(jsonPath("$.instance").value("/internal/v1/ledger/journals/by-transaction/" + txId));
    }

    @Test
    @DisplayName("Non-existent/public endpoint without header returns 401 - confirms no public user endpoints")
    void shouldReturn401ForUnauthenticatedNonExistentEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/public/ledger-data"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/accounts/{id}/balance with valid header authenticates and returns 404 when not found")
    void shouldAuthenticateAccountBalanceWithValidHeader() throws Exception {
        UUID accountId = UUID.randomUUID();
        mockMvc.perform(get("/internal/v1/ledger/accounts/{id}/balance", accountId)
                        .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, VALID_SERVICE_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /internal/v1/ledger/journals/by-transaction/{id} with valid header authenticates and returns 404 when not found")
    void shouldAuthenticateJournalWithValidHeader() throws Exception {
        UUID txId = UUID.randomUUID();
        mockMvc.perform(get("/internal/v1/ledger/journals/by-transaction/{transactionId}", txId)
                        .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, VALID_SERVICE_KEY))
                .andExpect(status().isNotFound());
    }
}
