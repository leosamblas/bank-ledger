package com.bankledger.transaction.integration;

import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.transaction.adapters.in.rest.dto.DepositRequest;
import com.bankledger.transaction.adapters.out.persistence.entity.IdempotencyKeyEntity;
import com.bankledger.transaction.adapters.out.persistence.repository.IdempotencySpringDataRepository;
import com.bankledger.transaction.adapters.out.persistence.repository.TransactionSpringDataRepository;
import com.bankledger.transaction.config.JwtTokenService;
import com.bankledger.transaction.domain.model.IdempotencyStatus;
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
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@Testcontainers
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Idempotency Concurrency Integration Test with Testcontainers")
class IdempotencyConcurrencyIntegrationTest {

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
    private final ObjectMapper objectMapper;

    private MockMvc mockMvc;

    public IdempotencyConcurrencyIntegrationTest(
            WebApplicationContext context,
            JwtTokenService jwtTokenService,
            TransactionSpringDataRepository transactionRepository,
            IdempotencySpringDataRepository idempotencyRepository,
            ObjectMapper objectMapper
    ) {
        this.context = context;
        this.jwtTokenService = jwtTokenService;
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
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
    @DisplayName("10 threads concurrentes com a mesma Idempotency-Key: exatamente 1 recebe 202 e 9 recebem 409 Conflict")
    void testConcurrentRequestsWithSameIdempotencyKey() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = jwtTokenService.generateToken(customerId, accountId);

        UUID idempotencyKey = UUID.randomUUID();
        DepositRequest depositRequest = new DepositRequest(50000L, "Depósito concorrente");
        String requestBody = objectMapper.writeValueAsString(depositRequest);

        int totalThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CyclicBarrier barrier = new CyclicBarrier(totalThreads);
        CountDownLatch latch = new CountDownLatch(totalThreads);

        List<Integer> responseStatuses = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger acceptedCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/transactions/deposits")
                                    .header(HttpHeaderConstants.IDEMPOTENCY_KEY, idempotencyKey.toString())
                                    .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    responseStatuses.add(status);

                    if (status == 202) {
                        acceptedCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    responseStatuses.add(-1);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(responseStatuses).hasSize(totalThreads);

        // Exatamente 1 deve vencer e receber 202; as demais 9 devem receber 409 Conflict
        assertThat(acceptedCount.get())
                .as("Exatamente 1 requisição deve receber HTTP 202 Accepted. Statuses: %s", responseStatuses)
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("Exatamente 9 requisições devem receber HTTP 409 Conflict. Statuses: %s", responseStatuses)
                .isEqualTo(9);

        // Validar que apenas 1 transação foi gravada no banco
        long totalTransactions = transactionRepository.count();
        assertThat(totalTransactions).isEqualTo(1);

        // Validar que o registro de idempotência está gravado como COMPLETED
        var idempotencyRecordOpt = idempotencyRepository.findByScopeAndIdemKey(customerId, idempotencyKey);
        assertThat(idempotencyRecordOpt).isPresent();
        IdempotencyKeyEntity entity = idempotencyRecordOpt.get();
        assertThat(entity.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(entity.getResponseStatus()).isEqualTo(202);
        assertThat(entity.getTransactionId()).isNotNull();
    }
}
