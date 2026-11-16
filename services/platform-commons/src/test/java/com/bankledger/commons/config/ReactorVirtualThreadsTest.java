package com.bankledger.commons.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ReactorVirtualThreadsTest {

    @Test
    @DisplayName("EnvironmentPostProcessor should set reactor virtual threads property")
    void environmentPostProcessorShouldSetProperty() {
        ReactorVirtualThreadsEnvironmentPostProcessor processor = new ReactorVirtualThreadsEnvironmentPostProcessor();
        processor.postProcessEnvironment(new MockEnvironment(), null);

        assertThat(System.getProperty(ReactorVirtualThreadsEnvironmentPostProcessor.REACTOR_VIRTUAL_THREADS_PROPERTY))
                .isEqualTo("true");
    }

    @Test
    @DisplayName("Schedulers.boundedElastic() should use virtual threads when system property is enabled")
    void boundedElasticShouldUseVirtualThreads() {
        System.setProperty(ReactorVirtualThreadsEnvironmentPostProcessor.REACTOR_VIRTUAL_THREADS_PROPERTY, "true");
        Schedulers.resetFactory();

        AtomicBoolean isVirtual = new AtomicBoolean(false);
        Mono.fromRunnable(() -> isVirtual.set(Thread.currentThread().isVirtual()))
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        assertThat(isVirtual.get())
                .as("Thread executing boundedElastic must be a Virtual Thread")
                .isTrue();
    }
}
