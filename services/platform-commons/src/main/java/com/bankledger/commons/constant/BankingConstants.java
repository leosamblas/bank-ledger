package com.bankledger.commons.constant;

/**
 * Core banking domain constants, shard limits, and default currencies.
 */
public final class BankingConstants {

    public static final String DEFAULT_CURRENCY = "BRL";
    public static final String CASH_SHARD_PREFIX = "SYS-CASH-";
    public static final int TOTAL_CASH_SHARDS = 16;

    private BankingConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
