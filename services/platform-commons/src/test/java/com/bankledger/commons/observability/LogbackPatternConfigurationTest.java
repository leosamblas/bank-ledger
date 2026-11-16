package com.bankledger.commons.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.pattern.PatternLayoutEncoderBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackPatternConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class MinimalConfig {
    }

    @Test
    @DisplayName("logback-spring.xml must define springAppName and include service name and tracing in patterns")
    void shouldValidateLogbackXmlDefinition() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertThat(is).as("logback-spring.xml must be present on the classpath").isNotNull();
            String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(xml)
                    .as("logback-spring.xml must define springAppName from spring.application.name")
                    .contains("<springProperty scope=\"context\" name=\"springAppName\" source=\"spring.application.name\"");

            assertThat(xml)
                    .as("CONSOLE_LOG_PATTERN must include colored service name block")
                    .contains("%clr([${springAppName}]){yellow}");

            assertThat(xml)
                    .as("CONSOLE_LOG_PATTERN must include colored correlation block")
                    .contains("%clr([%X{traceId:-},%X{spanId:-}]){cyan}");

            assertThat(xml)
                    .as("FILE_LOG_PATTERN must include service name block")
                    .contains("[${springAppName}]");
        }
    }

    @Test
    @DisplayName("Logback should configure console appender with service name from Spring environment")
    void shouldConfigureConsoleLogPatternWithServiceName() {
        try (ConfigurableApplicationContext app = new SpringApplicationBuilder(MinimalConfig.class)
                .web(WebApplicationType.NONE)
                .run()) {

            assertThat(app.isActive()).isTrue();

            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            assertThat(loggerContext).isNotNull();

            String springAppName = loggerContext.getProperty("springAppName");
            assertThat(springAppName)
                    .as("springAppName context property should match spring.application.name")
                    .isEqualTo("platform-commons-test");

            Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            Appender<ILoggingEvent> consoleAppender = rootLogger.getAppender("CONSOLE");
            assertThat(consoleAppender).isNotNull();

            assertThat(consoleAppender).isInstanceOf(ConsoleAppender.class);
            ConsoleAppender<ILoggingEvent> ca = (ConsoleAppender<ILoggingEvent>) consoleAppender;
            Encoder<ILoggingEvent> encoder = ca.getEncoder();
            assertThat(encoder).isInstanceOf(PatternLayoutEncoderBase.class);

            PatternLayoutEncoderBase<ILoggingEvent> ple = (PatternLayoutEncoderBase<ILoggingEvent>) encoder;
            assertThat(ple.getPattern())
                    .as("PatternLayoutEncoder must contain the resolved service name")
                    .contains("%clr([platform-commons-test]){yellow}")
                    .contains("%clr([%X{traceId:-},%X{spanId:-}]){cyan}");
        }
    }
}
