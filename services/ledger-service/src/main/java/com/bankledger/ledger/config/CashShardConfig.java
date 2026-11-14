package com.bankledger.ledger.config;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.ledger.adapters.out.cache.CashShardCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Spring configuration for cash shard infrastructure.
 * Warms up the in-memory cash shard cache on application startup.
 */
@Slf4j
@Configuration
public class CashShardConfig {

    public static final int SHARD_COUNT = BankingConstants.TOTAL_CASH_SHARDS;
    public static final String SHARD_CODE_PREFIX = BankingConstants.CASH_SHARD_PREFIX;

    @Bean
    public ApplicationRunner cashShardWarmupRunner(CashShardCache cashShardCache) {
        return args -> {
            log.info("Warming up cash shard cache on startup...");
            cashShardCache.loadShards();
        };
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
