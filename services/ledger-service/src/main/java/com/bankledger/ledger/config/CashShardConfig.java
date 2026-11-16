package com.bankledger.ledger.config;

import com.bankledger.ledger.adapters.out.cache.CashShardCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for cash shard infrastructure.
 * Warms up the in-memory cash shard cache on application startup.
 */
@Slf4j
@Configuration
public class CashShardConfig {

    @Bean
    public ApplicationRunner cashShardWarmupRunner(CashShardCache cashShardCache) {
        return args -> {
            log.info("Warming up cash shard cache on startup...");
            cashShardCache.loadShards();
        };
    }
}
