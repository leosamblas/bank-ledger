package com.bankledger.ledger.application.service;

import com.bankledger.commons.constant.BankingConstants;
import com.bankledger.ledger.application.port.out.CashShardPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the system cash shard (SYS-CASH-00 to SYS-CASH-15) for a transaction
 * according to the hybrid allocation policy (ADR 0001).
 *
 * <p>Allocation Rules:
 * <ol>
 *   <li>If {@code terminalId} contains a direct shard code (e.g. {@code SYS-CASH-11}),
 *       resolves directly to that shard.</li>
 *   <li>If {@code terminalId} is a generic terminal/agency string, resolves deterministically
 *       via {@code Math.floorMod(terminalId.hashCode(), 16)}.</li>
 *   <li>If {@code terminalId} is null or blank, falls back to transaction hash:
 *       {@code Math.floorMod(transactionId.hashCode(), 16)}.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CashShardResolver {

    private static final Pattern DIRECT_SHARD_PATTERN =
            Pattern.compile(BankingConstants.CASH_SHARD_PREFIX + "(0[0-9]|1[0-5])", Pattern.CASE_INSENSITIVE);

    private final CashShardPort cashShardPort;

    /**
     * Resolves the cash shard UUID according to the hybrid allocation policy.
     *
     * @param terminalId    optional terminal identifier (may be null or blank)
     * @param transactionId unique transaction UUID (required when terminalId is absent)
     * @return the UUID of the resolved cash shard ledger account
     */
    public UUID resolveShardId(String terminalId, UUID transactionId) {
        if (terminalId != null && !terminalId.isBlank()) {
            Matcher matcher = DIRECT_SHARD_PATTERN.matcher(terminalId);
            if (matcher.find()) {
                String directCode = matcher.group(0).toUpperCase(Locale.ROOT);
                return cashShardPort.getShardId(directCode);
            }
            int shardIndex = Math.floorMod(terminalId.hashCode(), BankingConstants.TOTAL_CASH_SHARDS);
            return cashShardPort.getShardIdByIndex(shardIndex);
        }

        if (transactionId == null) {
            throw new IllegalArgumentException("transactionId cannot be null when terminalId is absent");
        }
        int shardIndex = Math.floorMod(transactionId.hashCode(), BankingConstants.TOTAL_CASH_SHARDS);
        return cashShardPort.getShardIdByIndex(shardIndex);
    }

    /**
     * Overload to resolve cash shard when only transactionId is available.
     *
     * @param transactionId unique transaction UUID
     * @return the UUID of the resolved cash shard ledger account
     */
    public UUID resolveShardId(UUID transactionId) {
        return resolveShardId(null, transactionId);
    }

    /**
     * Resolves the canonical shard code (e.g., "SYS-CASH-11") for audit or logging purposes.
     *
     * @param terminalId    optional terminal identifier
     * @param transactionId unique transaction UUID
     * @return canonical shard code string
     */
    public String resolveShardCode(String terminalId, UUID transactionId) {
        if (terminalId != null && !terminalId.isBlank()) {
            Matcher matcher = DIRECT_SHARD_PATTERN.matcher(terminalId);
            if (matcher.find()) {
                return matcher.group(0).toUpperCase(Locale.ROOT);
            }
            int shardIndex = Math.floorMod(terminalId.hashCode(), BankingConstants.TOTAL_CASH_SHARDS);
            return formatShardCode(shardIndex);
        }

        if (transactionId == null) {
            throw new IllegalArgumentException("transactionId cannot be null when terminalId is absent");
        }
        int shardIndex = Math.floorMod(transactionId.hashCode(), BankingConstants.TOTAL_CASH_SHARDS);
        return formatShardCode(shardIndex);
    }

    private static String formatShardCode(int index) {
        return String.format(Locale.ROOT, "%s%02d", BankingConstants.CASH_SHARD_PREFIX, index);
    }
}
