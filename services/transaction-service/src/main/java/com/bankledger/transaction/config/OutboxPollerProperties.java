package com.bankledger.transaction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "bankledger.outbox.poller")
public class OutboxPollerProperties {

    private boolean enabled = true;
    private boolean autoStartup = true;
    private int batchSize = 50;
    private long intervalMs = 200;
    private int maxAttempts = 5;
    private long publishTimeoutMs = 2000;
}
