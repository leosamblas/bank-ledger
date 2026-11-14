package com.bankledger.commons.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({OpenTelemetry.class, OpenTelemetryAppender.class})
public class OpenTelemetryLoggingAutoConfiguration {

    @Bean
    static BeanPostProcessor openTelemetryAppenderInstaller() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof OpenTelemetry openTelemetry) {
                    ILoggerFactory factory = LoggerFactory.getILoggerFactory();
                    if (factory instanceof LoggerContext context) {
                        Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
                        if (rootLogger.getAppender("OTEL") == null) {
                            OpenTelemetryAppender appender = new OpenTelemetryAppender();
                            appender.setContext(context);
                            appender.setName("OTEL");
                            appender.setCaptureExperimentalAttributes(true);
                            appender.setCaptureMdcAttributes("*");
                            appender.setOpenTelemetry(openTelemetry);
                            appender.start();
                            rootLogger.addAppender(appender);
                        }
                    }
                    OpenTelemetryAppender.install(openTelemetry);
                }
                return bean;
            }
        };
    }
}
