package com.bankledger.transaction.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kafka Consumer Auto-Configuration Tests")
class KafkaConsumerConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withPropertyValues(
                    "spring.kafka.listener.ack-mode=manual_immediate",
                    "spring.kafka.consumer.enable-auto-commit=false"
            );

    @Test
    @DisplayName("Should auto-configure kafkaListenerContainerFactory with AckMode.MANUAL_IMMEDIATE")
    void shouldAutoConfigureFactoryWithManualImmediateAckMode() {
        runner.run(context -> {
            assertThat(context).hasBean("kafkaListenerContainerFactory");
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean("kafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.MANUAL_IMMEDIATE);
        });
    }
}
