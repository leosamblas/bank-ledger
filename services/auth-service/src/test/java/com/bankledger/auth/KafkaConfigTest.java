package com.bankledger.auth;

import com.bankledger.auth.config.KafkaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Kafka Configuration Tests for auth-service")
class KafkaConfigTest {

    @Test
    @DisplayName("Should configure KafkaListenerContainerFactory with AckMode.MANUAL_IMMEDIATE")
    @SuppressWarnings("unchecked")
    void shouldConfigureFactoryWithManualImmediateAckMode() {
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);
        KafkaConfig config = new KafkaConfig();

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory);

        assertThat(factory).isNotNull();
        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.MANUAL_IMMEDIATE);
        assertThat(factory.getConsumerFactory()).isSameAs(consumerFactory);
    }

    @Test
    @DisplayName("Should auto-configure kafkaListenerContainerFactory bean via KafkaConfig and KafkaAutoConfiguration")
    void shouldAutoConfigureContainerFactoryBean() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class, KafkaConfig.class))
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "spring.kafka.consumer.group-id=auth-service-test-group"
                );

        runner.run(context -> {
            assertThat(context).hasBean("kafkaListenerContainerFactory");
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean("kafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.MANUAL_IMMEDIATE);
        });
    }
}
