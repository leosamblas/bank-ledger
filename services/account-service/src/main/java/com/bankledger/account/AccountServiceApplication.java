package com.bankledger.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.bankledger.account", "com.bankledger.commons"})
@EntityScan(basePackages = {"com.bankledger.account", "com.bankledger.commons"})
@EnableJpaRepositories(basePackages = {"com.bankledger.account", "com.bankledger.commons"})
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
