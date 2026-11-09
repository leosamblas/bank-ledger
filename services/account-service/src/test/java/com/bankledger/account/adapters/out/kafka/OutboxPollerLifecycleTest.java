package com.bankledger.account.adapters.out.kafka;

import com.bankledger.account.config.OutboxPollerProperties;
import com.bankledger.commons.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class OutboxPollerLifecycleTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaOutboxPublisher publisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private OutboxPollerProperties properties;
    private OutboxPollerLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        properties = new OutboxPollerProperties();
        properties.setBatchSize(10);
        properties.setIntervalMs(100L);
        properties.setMaxAttempts(3);
        properties.setAutoStartup(false);

        lifecycle = new OutboxPollerLifecycle(repository, publisher, transactionManager, properties);
    }

    @Test
    @DisplayName("Should configure poller and autoStartup from properties")
    void shouldConfigureFromProperties() {
        assertThat(lifecycle.getProperties()).isSameAs(properties);
        assertThat(lifecycle.isAutoStartup()).isFalse();
        assertThat(lifecycle.getPoller().getBatchSize()).isEqualTo(10);
        assertThat(lifecycle.getPoller().getPollingIntervalMs()).isEqualTo(100L);
        assertThat(lifecycle.getPoller().getMaxAttempts()).isEqualTo(3);
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should validate null constructor arguments")
    void shouldValidateNullConstructorArguments() {
        assertThatThrownBy(() -> new OutboxPollerLifecycle(null, publisher, transactionManager, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository must not be null");

        assertThatThrownBy(() -> new OutboxPollerLifecycle(repository, null, transactionManager, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("publisher must not be null");

        assertThatThrownBy(() -> new OutboxPollerLifecycle(repository, publisher, null, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("transactionManager must not be null");

        assertThatThrownBy(() -> new OutboxPollerLifecycle(repository, publisher, transactionManager, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties must not be null");
    }

    @Test
    @DisplayName("Should transition running state on start and stop")
    void shouldTransitionRunningState() {
        assertThat(lifecycle.isRunning()).isFalse();

        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();

        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should invoke callback on stop(Runnable)")
    void shouldInvokeCallbackOnStop() {
        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();

        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        lifecycle.stop(() -> callbackInvoked.set(true));

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(callbackInvoked.get()).isTrue();
    }
}
