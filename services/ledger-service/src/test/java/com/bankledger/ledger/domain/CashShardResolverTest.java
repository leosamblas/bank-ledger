package com.bankledger.ledger.domain;

import com.bankledger.ledger.application.port.out.CashShardPort;
import com.bankledger.ledger.application.service.CashShardResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CashShardResolver - Hybrid Allocation Policy (ADR 0001)")
class CashShardResolverTest {

    private CashShardResolver resolver;
    private Map<String, UUID> shardsByCode;
    private Map<Integer, UUID> shardsByIndex;

    @BeforeEach
    void setUp() {
        shardsByCode = new HashMap<>();
        shardsByIndex = new HashMap<>();

        for (int i = 0; i < 16; i++) {
            String code = String.format("SYS-CASH-%02d", i);
            UUID shardId = UUID.nameUUIDFromBytes(code.getBytes());
            shardsByCode.put(code, shardId);
            shardsByIndex.put(i, shardId);
        }

        CashShardPort stubPort = new CashShardPort() {
            @Override
            public UUID getShardId(String code) {
                UUID id = shardsByCode.get(code);
                if (id == null) {
                    throw new IllegalArgumentException("Unknown cash shard code: " + code);
                }
                return id;
            }

            @Override
            public UUID getShardIdByIndex(int index) {
                if (index < 0 || index > 15) {
                    throw new IllegalArgumentException("Index out of bounds: " + index);
                }
                return shardsByIndex.get(index);
            }

            @Override
            public Map<String, UUID> getAllShards() {
                return Map.copyOf(shardsByCode);
            }
        };

        resolver = new CashShardResolver(stubPort);
    }

    @Nested
    @DisplayName("Rule 1: Direct Shard Code in Terminal ID")
    class Rule1DirectShardCode {

        @Test
        @DisplayName("Should resolve directly to SYS-CASH-11 when terminalId is SYS-CASH-11")
        void shouldResolveDirectShardCode11() {
            UUID txId = UUID.randomUUID();
            UUID expectedId = shardsByCode.get("SYS-CASH-11");

            UUID resolved = resolver.resolveShardId("SYS-CASH-11", txId);

            assertThat(resolved).isEqualTo(expectedId);
            assertThat(resolver.resolveShardCode("SYS-CASH-11", txId)).isEqualTo("SYS-CASH-11");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "SYS-CASH-00", "SYS-CASH-05", "SYS-CASH-10", "SYS-CASH-15"
        })
        @DisplayName("Should resolve exact shard codes from 00 to 15")
        void shouldResolveAllExactShardCodes(String shardCode) {
            UUID txId = UUID.randomUUID();
            UUID expectedId = shardsByCode.get(shardCode);

            UUID resolved = resolver.resolveShardId(shardCode, txId);

            assertThat(resolved).isEqualTo(expectedId);
            assertThat(resolver.resolveShardCode(shardCode, txId)).isEqualTo(shardCode);
        }

        @Test
        @DisplayName("Should resolve direct shard code case-insensitively")
        void shouldResolveCaseInsensitiveShardCode() {
            UUID txId = UUID.randomUUID();
            UUID expectedId = shardsByCode.get("SYS-CASH-07");

            UUID resolved = resolver.resolveShardId("sys-cash-07", txId);

            assertThat(resolved).isEqualTo(expectedId);
            assertThat(resolver.resolveShardCode("sys-cash-07", txId)).isEqualTo("SYS-CASH-07");
        }

        @Test
        @DisplayName("Should extract direct shard code embedded in complex terminal name")
        void shouldExtractEmbeddedShardCode() {
            UUID txId = UUID.randomUUID();
            UUID expectedId = shardsByCode.get("SYS-CASH-03");

            UUID resolved = resolver.resolveShardId("ATM-SYS-CASH-03-BRANCH-12", txId);

            assertThat(resolved).isEqualTo(expectedId);
            assertThat(resolver.resolveShardCode("ATM-SYS-CASH-03-BRANCH-12", txId)).isEqualTo("SYS-CASH-03");
        }

        @Test
        @DisplayName("Should fall back to hash when shard code number is out of 00-15 range")
        void shouldFallbackToHashWhenShardCodeOutOfRange() {
            UUID txId = UUID.randomUUID();
            // SYS-CASH-99 is not in range 00-15, so Rule 2 applies
            String terminal = "SYS-CASH-99";
            int expectedIndex = Math.abs(terminal.hashCode()) % 16;
            UUID expectedId = shardsByIndex.get(expectedIndex);

            UUID resolved = resolver.resolveShardId(terminal, txId);

            assertThat(resolved).isEqualTo(expectedId);
        }
    }

    @Nested
    @DisplayName("Rule 2: Generic Terminal ID String")
    class Rule2GenericTerminalString {

        @Test
        @DisplayName("Should resolve deterministically via Math.abs(terminalId.hashCode()) % 16")
        void shouldResolveGenericTerminalDeterministically() {
            UUID txId = UUID.randomUUID();
            String terminalId = "Caixa Central";
            int expectedIndex = Math.abs(terminalId.hashCode()) % 16;
            UUID expectedId = shardsByIndex.get(expectedIndex);

            UUID resolved = resolver.resolveShardId(terminalId, txId);

            assertThat(resolved).isEqualTo(expectedId);
            assertThat(resolver.resolveShardCode(terminalId, txId))
                    .isEqualTo(String.format("SYS-CASH-%02d", expectedIndex));
        }

        @Test
        @DisplayName("Should be idempotent across multiple calls with same terminalId")
        void shouldBeIdempotentForSameTerminalId() {
            UUID txId1 = UUID.randomUUID();
            UUID txId2 = UUID.randomUUID();
            String terminalId = "Agencia-0001-Term-9";

            UUID first = resolver.resolveShardId(terminalId, txId1);
            UUID second = resolver.resolveShardId(terminalId, txId2);

            assertThat(first).isEqualTo(second);
        }
    }

    @Nested
    @DisplayName("Rule 3: Null or Blank Terminal ID Fallback to Transaction ID")
    class Rule3TransactionFallback {

        @Test
        @DisplayName("Should resolve via Math.abs(transactionId.hashCode()) % 16 when terminalId is null")
        void shouldResolveFromTransactionIdWhenTerminalNull() {
            UUID txId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
            int expectedIndex = Math.abs(txId.hashCode()) % 16;
            UUID expectedId = shardsByIndex.get(expectedIndex);

            UUID resolved = resolver.resolveShardId(null, txId);

            assertThat(resolved).isEqualTo(expectedId);
            assertThat(resolver.resolveShardCode(null, txId))
                    .isEqualTo(String.format("SYS-CASH-%02d", expectedIndex));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n"})
        @DisplayName("Should resolve from transactionId when terminalId is empty or whitespace")
        void shouldResolveFromTransactionIdWhenTerminalBlank(String blankTerminal) {
            UUID txId = UUID.randomUUID();
            int expectedIndex = Math.abs(txId.hashCode()) % 16;
            UUID expectedId = shardsByIndex.get(expectedIndex);

            UUID resolved = resolver.resolveShardId(blankTerminal, txId);

            assertThat(resolved).isEqualTo(expectedId);
        }

        @Test
        @DisplayName("Should resolve identically via single-argument overload")
        void shouldResolveIdenticallyViaOverload() {
            UUID txId = UUID.randomUUID();

            UUID resolvedViaOverload = resolver.resolveShardId(txId);
            UUID resolvedViaNull = resolver.resolveShardId(null, txId);

            assertThat(resolvedViaOverload).isEqualTo(resolvedViaNull);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when both terminalId and transactionId are absent")
        void shouldThrowWhenBothTerminalAndTransactionAbsent() {
            assertThatThrownBy(() -> resolver.resolveShardId(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("transactionId");

            assertThatThrownBy(() -> resolver.resolveShardId("   ", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("transactionId");
        }
    }
}
