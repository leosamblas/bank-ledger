package com.bankledger.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.bankledger.transaction", "com.bankledger.commons"})
@EnableConfigurationProperties
@EntityScan(basePackages = {"com.bankledger.transaction", "com.bankledger.commons"})
@EnableJpaRepositories(basePackages = {"com.bankledger.transaction", "com.bankledger.commons"})
public class TransactionServiceApplication {

    static {
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
    }

    public static void main(String[] args) {
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
