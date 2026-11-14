package com.bankledger.ledger.application.port.out;

import com.bankledger.commons.constant.BankingConstants;

import java.util.Map;
import java.util.UUID;

/**
 * Outbound port providing access to the 16 pre-populated system cash shards (ADR 0001).
 */
public interface CashShardPort {

    int TOTAL_SHARDS = BankingConstants.TOTAL_CASH_SHARDS;
    String SHARD_PREFIX = BankingConstants.CASH_SHARD_PREFIX;

    /**
     * Retrieves the UUID for a specific shard code (e.g., "SYS-CASH-00").
     *
     * @param code canonical shard code
     * @return UUID of the ledger account
     */
    UUID getShardId(String code);

    /**
     * Retrieves the UUID for a specific shard index (0 to 15).
     *
     * @param index shard index in range [0, 15]
     * @return UUID of the ledger account
     */
    UUID getShardIdByIndex(int index);

    /**
     * Returns an unmodifiable snapshot of all 16 cash shards keyed by their code.
     *
     * @return map of shard code to account UUID
     */
    Map<String, UUID> getAllShards();
}
