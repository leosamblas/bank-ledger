package com.bankledger.commons.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPollerTest {

    // Simple pass-through TransactionOperations for isolated unit testing
    private static final TransactionOperations NO_OP_TRANSACTION_OPERATIONS = new TransactionOperations() {
        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    @Test
    @DisplayName("should poll and publish pending events successfully")
    void shouldPollAndPublishPendingEventsSuccessfully() {
        OutboxEvent event1 = OutboxEvent.pending("Account", UUID.randomUUID(), "AccountOpened", "topic-1", "k1", "{}");
        OutboxEvent event2 = OutboxEvent.pending("Account", UUID.randomUUID(), "MoneyDeposited", "topic-1", "k2", "{}");

        List<OutboxEvent> pending = new ArrayList<>(List.of(event1, event2));
        List<OutboxEvent> saved = new ArrayList<>();
        List<OutboxEvent> published = new ArrayList<>();

        OutboxEventRepository stubRepo = createStubRepository(pending, saved);
        OutboxPublisher stubPublisher = published::add;

        OutboxPoller poller = new OutboxPoller(
                stubRepo,
                stubPublisher,
                NO_OP_TRANSACTION_OPERATIONS,
                50,
                200,
                5
        );

        int count = poller.pollAndPublish();

        assertThat(count).isEqualTo(2);
        assertThat(published).containsExactly(event1, event2);
        assertThat(event1.isPublished()).isTrue();
        assertThat(event1.getPublishedAt()).isNotNull();
        assertThat(event2.isPublished()).isTrue();
        assertThat(event2.getPublishedAt()).isNotNull();
        assertThat(saved).contains(event1, event2);
    }

    @Test
    @DisplayName("should increment attempts and not set publishedAt when publisher throws exception")
    void shouldIncrementAttemptsOnFailure() {
        OutboxEvent event = OutboxEvent.pending("Account", UUID.randomUUID(), "AccountOpened", "topic-1", "k1", "{}");
        List<OutboxEvent> pending = new ArrayList<>(List.of(event));
        List<OutboxEvent> saved = new ArrayList<>();

        OutboxEventRepository stubRepo = createStubRepository(pending, saved);
        OutboxPublisher failingPublisher = e -> {
            throw new RuntimeException("Kafka unreachable");
        };

        OutboxPoller poller = new OutboxPoller(
                stubRepo,
                failingPublisher,
                NO_OP_TRANSACTION_OPERATIONS,
                50,
                200,
                5
        );

        int count = poller.pollAndPublish();

        assertThat(count).isEqualTo(0);
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(saved).contains(event);
    }

    @Test
    @DisplayName("should skip event when max attempts exceeded")
    void shouldSkipEventWhenMaxAttemptsExceeded() {
        OutboxEvent event = OutboxEvent.pending("Account", UUID.randomUUID(), "AccountOpened", "topic-1", "k1", "{}");
        event.setAttempts(5); // maxAttempts = 5

        List<OutboxEvent> pending = new ArrayList<>(List.of(event));
        List<OutboxEvent> saved = new ArrayList<>();
        AtomicInteger publishCalls = new AtomicInteger(0);

        OutboxEventRepository stubRepo = createStubRepository(pending, saved);
        OutboxPublisher publisher = e -> publishCalls.incrementAndGet();

        OutboxPoller poller = new OutboxPoller(
                stubRepo,
                publisher,
                NO_OP_TRANSACTION_OPERATIONS,
                50,
                200,
                5
        );

        int count = poller.pollAndPublish();

        assertThat(count).isEqualTo(0);
        assertThat(publishCalls.get()).isEqualTo(0);
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    @DisplayName("should start and stop using Java virtual threads")
    void shouldStartAndStopUsingVirtualThread() throws Exception {
        OutboxEventRepository stubRepo = createStubRepository(new ArrayList<>(), new ArrayList<>());
        OutboxPublisher noOpPublisher = e -> {};

        OutboxPoller poller = new OutboxPoller(
                stubRepo,
                noOpPublisher,
                NO_OP_TRANSACTION_OPERATIONS,
                10,
                50,
                3
        );

        poller.start();
        assertThat(poller.isRunning()).isTrue();
        assertThat(poller.getPollerThread()).isNotNull();
        assertThat(poller.getPollerThread().isVirtual()).isTrue();
        assertThat(poller.getPollerThread().getName()).startsWith("outbox-poller-");

        poller.stop();
        assertThat(poller.isRunning()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private OutboxEventRepository createStubRepository(List<OutboxEvent> pending, List<OutboxEvent> saved) {
        return (OutboxEventRepository) java.lang.reflect.Proxy.newProxyInstance(
                OutboxEventRepository.class.getClassLoader(),
                new Class<?>[]{OutboxEventRepository.class},
                (proxy, method, args) -> {
                    if ("lockPendingEvents".equals(method.getName())) {
                        int batchSize = (int) args[0];
                        return pending.stream().limit(batchSize).toList();
                    }
                    if ("save".equals(method.getName())) {
                        saved.add((OutboxEvent) args[0]);
                        return args[0];
                    }
                    return null;
                }
        );
    }
}
