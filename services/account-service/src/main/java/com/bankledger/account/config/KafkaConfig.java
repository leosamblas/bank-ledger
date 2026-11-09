package com.bankledger.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

/**
 * Kafka infrastructure configuration for account-service.
 * <p>
 * Configures the {@link ConcurrentKafkaListenerContainerFactory} with
 * {@link AckMode#MANUAL_IMMEDIATE} to guarantee that all Kafka consumer listeners
 * have explicit, fine-grained control over partition offset commits via
 * {@code Acknowledgment.acknowledge()}, preventing automatic offset advancement
 * before business transactions complete.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
