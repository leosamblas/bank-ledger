package com.bankledger.transaction.config;

import com.bankledger.transaction.domain.service.BankingProtocolGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainServiceConfig {

    @Bean
    @ConditionalOnMissingBean
    public BankingProtocolGenerator bankingProtocolGenerator(Clock clock) {
        return new BankingProtocolGenerator(clock);
    }
}
