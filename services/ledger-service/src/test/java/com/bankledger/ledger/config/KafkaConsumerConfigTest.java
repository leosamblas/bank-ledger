package com.bankledger.ledger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @Test
    @DisplayName("Should configure KafkaListenerContainerFactory with AckMode.MANUAL_IMMEDIATE")
    @SuppressWarnings("unchecked")
    void shouldConfigureFactoryWithManualImmediateAckMode() {
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);
        KafkaConsumerConfig config = new KafkaConsumerConfig();

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory);

        assertThat(factory).isNotNull();
        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.MANUAL_IMMEDIATE);
        assertThat(factory.getConsumerFactory()).isSameAs(consumerFactory);
    }
}
