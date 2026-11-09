package com.bankledger.commons.inbox;

import com.bankledger.commons.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestApplication.class)
@Testcontainers
class InboxIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("schema/outbox-inbox-schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private InboxEventRepository repository;

    @Autowired
    private InboxHandler inboxHandler;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("calling execute twice with the same event ID and handler executes business action exactly once")
    void callingExecuteTwiceExecutesActionExactlyOnce() {
        UUID eventId = UUID.randomUUID();
        String handlerName = "AccountCreationEventHandler";
        AtomicInteger executionCount = new AtomicInteger(0);

        // First execution: should run business logic and persist inbox record
        inboxHandler.execute(eventId, handlerName, executionCount::incrementAndGet);

        assertThat(executionCount.get()).isEqualTo(1);
        assertThat(repository.existsByEventIdAndHandler(eventId, handlerName)).isTrue();

        Optional<InboxEvent> recorded = repository.findById(new InboxEventId(eventId, handlerName));
        assertThat(recorded).isPresent();
        assertThat(recorded.get().getEventId()).isEqualTo(eventId);
        assertThat(recorded.get().getHandler()).isEqualTo(handlerName);
        assertThat(recorded.get().getProcessedAt()).isNotNull();

        // Second execution with same eventId and handler: should skip business logic
        inboxHandler.execute(eventId, handlerName, executionCount::incrementAndGet);

        assertThat(executionCount.get())
                .as("Execution count should remain 1 because duplicate execution was skipped")
                .isEqualTo(1);

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("transactional rollback on business logic exception leaves inbox empty")
    void transactionalRollbackOnExceptionLeavesInboxEmpty() {
        UUID eventId = UUID.randomUUID();
        String handlerName = "FailingEventHandler";

        assertThatThrownBy(() -> inboxHandler.execute(eventId, handlerName, () -> {
            throw new IllegalStateException("Simulated business processing error");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated business processing error");

        // Verify that no inbox event was recorded due to transaction rollback
        assertThat(repository.existsByEventIdAndHandler(eventId, handlerName)).isFalse();
        assertThat(repository.findById(new InboxEventId(eventId, handlerName))).isEmpty();
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("different handlers can process the same eventId independently")
    void differentHandlersProcessSameEventIdIndependently() {
        UUID eventId = UUID.randomUUID();
        String handlerA = "EmailNotificationHandler";
        String handlerB = "AuditLogHandler";

        AtomicInteger counterA = new AtomicInteger(0);
        AtomicInteger counterB = new AtomicInteger(0);

        inboxHandler.execute(eventId, handlerA, counterA::incrementAndGet);
        inboxHandler.execute(eventId, handlerB, counterB::incrementAndGet);

        assertThat(counterA.get()).isEqualTo(1);
        assertThat(counterB.get()).isEqualTo(1);

        assertThat(repository.existsByEventIdAndHandler(eventId, handlerA)).isTrue();
        assertThat(repository.existsByEventIdAndHandler(eventId, handlerB)).isTrue();
        assertThat(repository.count()).isEqualTo(2);

        // Re-executing handler A skips execution, but does not affect handler B
        inboxHandler.execute(eventId, handlerA, counterA::incrementAndGet);
        assertThat(counterA.get()).isEqualTo(1);
        assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("consumer overload executes consumer with payload idempotently")
    void consumerOverloadExecutesWithPayloadIdempotently() {
        UUID eventId = UUID.randomUUID();
        String handlerName = "BalanceConsumer";
        AtomicReference<String> processedPayload = new AtomicReference<>(null);

        inboxHandler.execute(eventId, handlerName, processedPayload::set, "initial-balance-100");
        assertThat(processedPayload.get()).isEqualTo("initial-balance-100");

        // Calling again with different payload skips execution
        inboxHandler.execute(eventId, handlerName, processedPayload::set, "different-balance-200");
        assertThat(processedPayload.get()).isEqualTo("initial-balance-100");
    }

    @Test
    @DisplayName("concurrent executions for same event and handler are handled gracefully")
    void concurrentExecutionsHandledGracefully() throws Exception {
        UUID eventId = UUID.randomUUID();
        String handlerName = "ConcurrentHandler";
        AtomicInteger executionCount = new AtomicInteger(0);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);

        Runnable task = () -> {
            try {
                startGate.await();
                inboxHandler.execute(eventId, handlerName, () -> {
                    executionCount.incrementAndGet();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception ignored) {
            } finally {
                endGate.countDown();
            }
        };

        Thread t1 = Thread.ofVirtual().start(task);
        Thread t2 = Thread.ofVirtual().start(task);

        startGate.countDown();
        assertThat(endGate.await(10, TimeUnit.SECONDS)).isTrue();
        t1.join();
        t2.join();

        assertThat(repository.existsByEventIdAndHandler(eventId, handlerName)).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }
}
