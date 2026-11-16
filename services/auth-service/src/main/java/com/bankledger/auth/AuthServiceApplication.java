package com.bankledger.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication(exclude = {
        ReactiveUserDetailsServiceAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
public class AuthServiceApplication {

    static {
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
    }

    public static void main(String[] args) {
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
