package com.bankledger.commons.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Early Spring Boot environment post-processor that configures Project Reactor
 * to use Java 21+ Virtual Threads for {@code Schedulers.boundedElastic()}.
 * <p>
 * This property must be set as a JVM system property prior to the initialization
 * of Reactor schedulers.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReactorVirtualThreadsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String REACTOR_VIRTUAL_THREADS_PROPERTY = "reactor.schedulers.defaultBoundedElasticOnVirtualThreads";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.setProperty(REACTOR_VIRTUAL_THREADS_PROPERTY, "true");
    }
}
