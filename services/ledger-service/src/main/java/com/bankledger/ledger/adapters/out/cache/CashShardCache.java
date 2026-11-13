package com.bankledger.ledger.adapters.out.cache;

import com.bankledger.ledger.application.port.out.CashShardPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for the 16 pre-populated system cash shards (SYS-CASH-00 to SYS-CASH-15).
 * Loads system accounts from {@code ledger_account} on startup.
 */
@Slf4j
@Component
public class CashShardCache implements CashShardPort {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, UUID> shardsByCode = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> shardsByIndex = new ConcurrentHashMap<>();

    public CashShardCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads all system cash shards from the database into the in-memory cache.
     */
    public synchronized void loadShards() {
        if (jdbcTemplate == null) {
            log.warn("JdbcTemplate is null; skipping cash shard cache database load");
            return;
        }

        log.info("Loading system cash shards from ledger_account into memory cache...");
        String sql = "SELECT id, code FROM ledger_account WHERE kind = 'SYSTEM' ORDER BY code";
        jdbcTemplate.query(sql, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            String code = rs.getString("code");
            if (id != null && code != null) {
                registerShard(code, id);
            }
        });
        log.info("Loaded {} system cash shards into memory cache", shardsByCode.size());
    }

    /**
     * Manually registers a shard into the cache. Useful for testing or warmup.
     */
    public void registerShard(String code, UUID id) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(id, "id must not be null");

        shardsByCode.put(code, id);
        if (code.startsWith(SHARD_PREFIX)) {
            try {
                int index = Integer.parseInt(code.substring(SHARD_PREFIX.length()));
                shardsByIndex.put(index, id);
            } catch (NumberFormatException ignored) {
                log.warn("Non-numeric suffix for cash shard code: {}", code);
            }
        }
    }

    @Override
    public UUID getShardId(String code) {
        Objects.requireNonNull(code, "code must not be null");
        ensureLoaded();
        UUID id = shardsByCode.get(code);
        if (id == null) {
            throw new IllegalArgumentException("Unknown cash shard code: " + code);
        }
        return id;
    }

    @Override
    public UUID getShardIdByIndex(int index) {
        if (index < 0 || index >= TOTAL_SHARDS) {
            throw new IllegalArgumentException(
                    "Cash shard index must be between 0 and " + (TOTAL_SHARDS - 1) + ": " + index);
        }
        ensureLoaded();
        UUID id = shardsByIndex.get(index);
        if (id == null) {
            String code = String.format("%s%02d", SHARD_PREFIX, index);
            id = shardsByCode.get(code);
        }
        if (id == null) {
            throw new IllegalArgumentException("Cash shard not found for index: " + index);
        }
        return id;
    }

    @Override
    public Map<String, UUID> getAllShards() {
        ensureLoaded();
        return Collections.unmodifiableMap(new HashMap<>(shardsByCode));
    }

    private void ensureLoaded() {
        if (shardsByCode.isEmpty() && jdbcTemplate != null) {
            loadShards();
        }
    }
}
