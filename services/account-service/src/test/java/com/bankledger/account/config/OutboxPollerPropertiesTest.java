package com.bankledger.account.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPollerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(OutboxPollerProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("Should have correct default values when no properties are configured")
    void shouldHaveDefaultValues() {
        OutboxPollerProperties properties = new OutboxPollerProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isAutoStartup()).isTrue();
        assertThat(properties.getBatchSize()).isEqualTo(50);
        assertThat(properties.getIntervalMs()).isEqualTo(200L);
        assertThat(properties.getMaxAttempts()).isEqualTo(5);
        assertThat(properties.getPublishTimeoutMs()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("Should bind default values in Spring context when no properties are overridden")
    void shouldBindDefaultValuesInSpringContext() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxPollerProperties.class);

            OutboxPollerProperties properties = context.getBean(OutboxPollerProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.isAutoStartup()).isTrue();
            assertThat(properties.getBatchSize()).isEqualTo(50);
            assertThat(properties.getIntervalMs()).isEqualTo(200L);
            assertThat(properties.getMaxAttempts()).isEqualTo(5);
            assertThat(properties.getPublishTimeoutMs()).isEqualTo(2000L);
        });
    }

    @Test
    @DisplayName("Should bind custom properties from environment or configuration")
    void shouldBindCustomProperties() {
        contextRunner
                .withPropertyValues(
                        "bankledger.outbox.poller.enabled=false",
                        "bankledger.outbox.poller.auto-startup=false",
                        "bankledger.outbox.poller.batch-size=100",
                        "bankledger.outbox.poller.interval-ms=500",
                        "bankledger.outbox.poller.max-attempts=10",
                        "bankledger.outbox.poller.publish-timeout-ms=5000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OutboxPollerProperties.class);

                    OutboxPollerProperties properties = context.getBean(OutboxPollerProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.isAutoStartup()).isFalse();
                    assertThat(properties.getBatchSize()).isEqualTo(100);
                    assertThat(properties.getIntervalMs()).isEqualTo(500L);
                    assertThat(properties.getMaxAttempts()).isEqualTo(10);
                    assertThat(properties.getPublishTimeoutMs()).isEqualTo(5000L);
                });
    }

    @Test
    @DisplayName("Should support setters and getters")
    void shouldSupportSettersAndGetters() {
        OutboxPollerProperties properties = new OutboxPollerProperties();
        properties.setEnabled(false);
        properties.setAutoStartup(false);
        properties.setBatchSize(25);
        properties.setIntervalMs(1000L);
        properties.setMaxAttempts(3);
        properties.setPublishTimeoutMs(3000L);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isAutoStartup()).isFalse();
        assertThat(properties.getBatchSize()).isEqualTo(25);
        assertThat(properties.getIntervalMs()).isEqualTo(1000L);
        assertThat(properties.getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getPublishTimeoutMs()).isEqualTo(3000L);
    }
}
