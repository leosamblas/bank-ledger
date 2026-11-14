package com.bankledger.commons.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryLoggingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetryLoggingAutoConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

    @Test
    void shouldInstallOpenTelemetryAppenderOnRootLogger() {
        contextRunner.withUserConfiguration(TestConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                    Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
                    var appender = rootLogger.getAppender("OTEL");

                    assertThat(appender).isNotNull();
                    assertThat(appender).isInstanceOf(OpenTelemetryAppender.class);
                    assertThat(appender.isStarted()).isTrue();

                    // Verify logging an event does not throw
                    org.slf4j.Logger log = LoggerFactory.getLogger(OpenTelemetryLoggingAutoConfigurationTest.class);
                    log.info("Test log event for OpenTelemetry appender verification");
                });
    }
}
