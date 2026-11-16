package com.bankledger.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.bankledger.query", "com.bankledger.commons"})
@EntityScan(basePackages = {"com.bankledger.query", "com.bankledger.commons"})
@EnableJpaRepositories(basePackages = {"com.bankledger.query", "com.bankledger.commons"})
public class QueryServiceApplication {

    static {
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
    }

    public static void main(String[] args) {
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        SpringApplication.run(QueryServiceApplication.class, args);
    }
}
