package com.bankledger.ledger.adapters.out.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@DisplayName("CashShardCache - In-Memory Shard Cache")
class CashShardCacheTest {

    @Nested
    @DisplayName("Manual Registration & Lookups")
    class ManualRegistrationTests {

        @Test
        @DisplayName("Should retrieve shard by code and by index after manual registration")
        void shouldRetrieveShardByCodeAndIndex() {
            CashShardCache cache = new CashShardCache(null);
            UUID shardId00 = UUID.randomUUID();
            UUID shardId15 = UUID.randomUUID();

            cache.registerShard("SYS-CASH-00", shardId00);
            cache.registerShard("SYS-CASH-15", shardId15);

            assertThat(cache.getShardId("SYS-CASH-00")).isEqualTo(shardId00);
            assertThat(cache.getShardIdByIndex(0)).isEqualTo(shardId00);
            assertThat(cache.getShardId("SYS-CASH-15")).isEqualTo(shardId15);
            assertThat(cache.getShardIdByIndex(15)).isEqualTo(shardId15);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when shard code is not found")
        void shouldThrowWhenShardCodeNotFound() {
            CashShardCache cache = new CashShardCache(null);

            assertThatThrownBy(() -> cache.getShardId("SYS-CASH-99"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown cash shard code");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when index is out of bounds")
        void shouldThrowWhenIndexOutOfBounds() {
            CashShardCache cache = new CashShardCache(null);

            assertThatThrownBy(() -> cache.getShardIdByIndex(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 15");

            assertThatThrownBy(() -> cache.getShardIdByIndex(16))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 15");
        }

        @Test
        @DisplayName("Should return unmodifiable map of all registered shards")
        void shouldReturnUnmodifiableMap() {
            CashShardCache cache = new CashShardCache(null);
            UUID id0 = UUID.randomUUID();
            cache.registerShard("SYS-CASH-00", id0);

            Map<String, UUID> shards = cache.getAllShards();
            assertThat(shards).containsEntry("SYS-CASH-00", id0);

            assertThatThrownBy(() -> shards.put("SYS-CASH-01", UUID.randomUUID()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Database Loading via JdbcTemplate")
    class DatabaseLoadingTests {

        @Test
        @DisplayName("Should load 16 shards from database query")
        void shouldLoadShardsFromDatabase() throws SQLException {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            CashShardCache cache = new CashShardCache(jdbcTemplate);

            doAnswer(invocation -> {
                RowCallbackHandler handler = invocation.getArgument(1);
                for (int i = 0; i < 16; i++) {
                    ResultSet rs = mock(ResultSet.class);
                    UUID id = UUID.randomUUID();
                    String code = String.format("SYS-CASH-%02d", i);
                    org.mockito.Mockito.when(rs.getObject("id", UUID.class)).thenReturn(id);
                    org.mockito.Mockito.when(rs.getString("code")).thenReturn(code);
                    handler.processRow(rs);
                }
                return null;
            }).when(jdbcTemplate).query(eq("SELECT id, code FROM ledger_account WHERE kind = 'SYSTEM' ORDER BY code"), any(RowCallbackHandler.class));

            cache.loadShards();

            assertThat(cache.getAllShards()).hasSize(16);
            for (int i = 0; i < 16; i++) {
                String code = String.format("SYS-CASH-%02d", i);
                assertThat(cache.getShardId(code)).isNotNull();
                assertThat(cache.getShardIdByIndex(i)).isEqualTo(cache.getShardId(code));
            }
        }
    }
}
