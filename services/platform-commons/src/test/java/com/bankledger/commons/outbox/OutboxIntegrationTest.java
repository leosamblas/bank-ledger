package com.bankledger.commons.outbox;

import com.bankledger.commons.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OutboxIntegrationTest {

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

    private final OutboxEventRepository repository;
    private final PlatformTransactionManager transactionManager;

    public OutboxIntegrationTest(OutboxEventRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionManager = transactionManager;
    }

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("should lock and publish pending outbox events, setting publishedAt")
    void shouldLockAndPublishPendingEvents() {
        // Arrange
        UUID agg1 = UUID.randomUUID();
        UUID agg2 = UUID.randomUUID();
        OutboxEvent event1 = OutboxEvent.pending("Account", agg1, "AccountOpened", "account.events", agg1.toString(), "{\"id\":\"" + agg1 + "\"}");
        OutboxEvent event2 = OutboxEvent.pending("Account", agg2, "MoneyDeposited", "account.events", agg2.toString(), "{\"id\":\"" + agg2 + "\"}");

        repository.saveAll(List.of(event1, event2));

        List<OutboxEvent> published = new CopyOnWriteArrayList<>();
        OutboxPublisher publisher = published::add;

        try (OutboxPoller poller = new OutboxPoller(repository, publisher, transactionManager, 50, 200, 5)) {
            // Act
            int count = poller.pollAndPublish();

            // Assert
            assertThat(count).isEqualTo(2);
            assertThat(published).hasSize(2);

            List<OutboxEvent> allInDb = repository.findAll();
            assertThat(allInDb).hasSize(2);
            for (OutboxEvent dbEvent : allInDb) {
                assertThat(dbEvent.isPublished()).isTrue();
                assertThat(dbEvent.getPublishedAt()).isNotNull();
                assertThat(dbEvent.getAttempts()).isEqualTo(0);
            }

            // Subsequent poll should find 0 pending
            int nextPollCount = poller.pollAndPublish();
            assertThat(nextPollCount).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("concurrent workers running lockPendingEvents with FOR UPDATE SKIP LOCKED do not lock duplicate events")
    void concurrentWorkersDoNotLockOverlappingEvents() throws Exception {
        // Arrange: insert 20 events
        List<OutboxEvent> events = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID aggId = UUID.randomUUID();
            events.add(OutboxEvent.pending("Account", aggId, "Event" + i, "account.events", "k" + i, "{\"seq\":" + i + "}"));
        }
        repository.saveAll(events);

        CountDownLatch worker1Locked = new CountDownLatch(1);
        CountDownLatch worker2Done = new CountDownLatch(1);

        List<UUID> worker1Ids = new ArrayList<>();
        List<UUID> worker2Ids = new ArrayList<>();

        // Worker 1 runs on a Virtual Thread and holds the transaction lock until Worker 2 has executed
        Thread worker1 = Thread.ofVirtual().name("test-worker-1").start(() -> {
            new TransactionTemplate(transactionManager).execute(status -> {
                List<OutboxEvent> batch = repository.lockPendingEvents(10);
                for (OutboxEvent e : batch) {
                    worker1Ids.add(e.getId());
                }
                // Signal Worker 1 has acquired locks
                worker1Locked.countDown();

                // Wait until Worker 2 has also queried with SKIP LOCKED before releasing transaction
                try {
                    worker2Done.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        // Worker 2 waits for Worker 1 to hold the lock, then queries in a separate transaction
        Thread worker2 = Thread.ofVirtual().name("test-worker-2").start(() -> {
            try {
                // Wait for worker 1 to have locked first 10 rows
                assertThat(worker1Locked.await(10, TimeUnit.SECONDS)).isTrue();

                new TransactionTemplate(transactionManager).execute(status -> {
                    List<OutboxEvent> batch = repository.lockPendingEvents(10);
                    for (OutboxEvent e : batch) {
                        worker2Ids.add(e.getId());
                    }
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                worker2Done.countDown();
            }
        });

        worker1.join(Duration.ofSeconds(15));
        worker2.join(Duration.ofSeconds(15));

        // Assert: both workers got 10 records and they are completely disjoint
        assertThat(worker1Ids).hasSize(10);
        assertThat(worker2Ids).hasSize(10);
        assertThat(Collections.disjoint(worker1Ids, worker2Ids))
                .as("Worker 1 and Worker 2 must have no overlapping events due to SKIP LOCKED")
                .isTrue();

        Set<UUID> allLockedIds = new HashSet<>(worker1Ids);
        allLockedIds.addAll(worker2Ids);
        assertThat(allLockedIds).hasSize(20);
    }

    @Test
    @DisplayName("publisher failure increments attempts counter and leaves publishedAt as null")
    void failureIncrementsAttemptsWithoutSettingPublishedAt() {
        // Arrange
        UUID aggId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.pending(
                "Account",
                aggId,
                "AccountOpened",
                "account.events",
                aggId.toString(),
                "{\"id\":\"" + aggId + "\"}"
        );
        repository.save(event);

        AtomicInteger failureAttempts = new AtomicInteger(0);
        OutboxPublisher failingPublisher = e -> {
            failureAttempts.incrementAndGet();
            throw new RuntimeException("Simulated Kafka broker network outage");
        };

        try (OutboxPoller poller = new OutboxPoller(repository, failingPublisher, transactionManager, 50, 200, 5)) {
            // Act: first failure
            int processed1 = poller.pollAndPublish();
            assertThat(processed1).isEqualTo(0);

            OutboxEvent afterFirstFail = repository.findById(event.getId()).orElseThrow();
            assertThat(afterFirstFail.getAttempts()).isEqualTo(1);
            assertThat(afterFirstFail.getPublishedAt()).isNull();
            assertThat(afterFirstFail.isPublished()).isFalse();

            // Act: second failure
            int processed2 = poller.pollAndPublish();
            assertThat(processed2).isEqualTo(0);

            OutboxEvent afterSecondFail = repository.findById(event.getId()).orElseThrow();
            assertThat(afterSecondFail.getAttempts()).isEqualTo(2);
            assertThat(afterSecondFail.getPublishedAt()).isNull();
            assertThat(afterSecondFail.isPublished()).isFalse();

            assertThat(failureAttempts.get()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("background poller virtual thread successfully publishes events via lifecycle start and stop")
    void backgroundPollerVirtualThreadPublishesEvents() throws Exception {
        UUID aggId1 = UUID.randomUUID();
        UUID aggId2 = UUID.randomUUID();
        repository.saveAll(List.of(
                OutboxEvent.pending("Account", aggId1, "E1", "topic", "k1", "{}"),
                OutboxEvent.pending("Account", aggId2, "E2", "topic", "k2", "{}")
        ));

        List<OutboxEvent> published = new CopyOnWriteArrayList<>();
        OutboxPublisher publisher = published::add;

        try (OutboxPoller poller = new OutboxPoller(repository, publisher, transactionManager, 10, 50, 3)) {
            poller.start();
            assertThat(poller.isRunning()).isTrue();
            assertThat(poller.getPollerThread().isVirtual()).isTrue();

            org.awaitility.Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> repository.findAll().stream().allMatch(OutboxEvent::isPublished));

            poller.stop();
            assertThat(poller.isRunning()).isFalse();
        }

        List<OutboxEvent> inDb = repository.findAll();
        assertThat(inDb).hasSize(2);
        assertThat(inDb).allMatch(OutboxEvent::isPublished);
        assertThat(published).hasSize(2);
    }
}
