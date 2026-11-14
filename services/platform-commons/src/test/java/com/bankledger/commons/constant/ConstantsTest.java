package com.bankledger.commons.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstantsTest {

    @Test
    @DisplayName("KafkaTopics should define canonical topics and prevent instantiation")
    void verifyKafkaTopics() throws Exception {
        assertThat(KafkaTopics.ACCOUNT_EVENTS_V1).isEqualTo("account.events.v1");
        assertThat(KafkaTopics.LEDGER_COMMANDS_V1).isEqualTo("ledger.commands.v1");
        assertThat(KafkaTopics.LEDGER_JOURNAL_RESULTS_V1).isEqualTo("ledger.journal-results.v1");
        assertThat(KafkaTopics.LEDGER_ACCOUNT_EVENTS_V1).isEqualTo("ledger.account-events.v1");
        assertThat(KafkaTopics.LEDGER_ENTRIES_V1).isEqualTo("ledger.entries.v1");
        assertThat(KafkaTopics.TRANSACTION_EVENTS_V1).isEqualTo("transaction.events.v1");
        assertThat(KafkaTopics.DEAD_LETTER_TOPIC_SUFFIX).isEqualTo(".dlt");

        assertUtilityClassWellDefined(KafkaTopics.class);
    }

    @Test
    @DisplayName("BankingErrorCodes should define standard error codes and prevent instantiation")
    void verifyBankingErrorCodes() throws Exception {
        assertThat(BankingErrorCodes.INSUFFICIENT_FUNDS).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(BankingErrorCodes.ACCOUNT_NOT_FOUND).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(BankingErrorCodes.ACCOUNT_BLOCKED).isEqualTo("ACCOUNT_BLOCKED");
        assertThat(BankingErrorCodes.LIMIT_EXCEEDED).isEqualTo("LIMIT_EXCEEDED");
        assertThat(BankingErrorCodes.IDEMPOTENCY_CONFLICT).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(BankingErrorCodes.IDEMPOTENCY_KEY_REUSED).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(BankingErrorCodes.IDEMPOTENCY_REUSED).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(BankingErrorCodes.INVALID_TRANSACTION).isEqualTo("INVALID_TRANSACTION");
        assertThat(BankingErrorCodes.INTERNAL_ERROR).isEqualTo("INTERNAL_ERROR");

        assertUtilityClassWellDefined(BankingErrorCodes.class);
    }

    @Test
    @DisplayName("HttpHeaderConstants should define standard headers and prevent instantiation")
    void verifyHttpHeaderConstants() throws Exception {
        assertThat(HttpHeaderConstants.IDEMPOTENCY_KEY).isEqualTo("Idempotency-Key");
        assertThat(HttpHeaderConstants.AUTHORIZATION).isEqualTo("Authorization");
        assertThat(HttpHeaderConstants.BEARER_PREFIX).isEqualTo("Bearer ");
        assertThat(HttpHeaderConstants.LOCATION).isEqualTo("Location");
        assertThat(HttpHeaderConstants.CONTENT_TYPE).isEqualTo("Content-Type");

        assertUtilityClassWellDefined(HttpHeaderConstants.class);
    }

    @Test
    @DisplayName("BankingConstants should define domain constants and prevent instantiation")
    void verifyBankingConstants() throws Exception {
        assertThat(BankingConstants.DEFAULT_CURRENCY).isEqualTo("BRL");
        assertThat(BankingConstants.CASH_SHARD_PREFIX).isEqualTo("SYS-CASH-");
        assertThat(BankingConstants.TOTAL_CASH_SHARDS).isEqualTo(16);

        assertUtilityClassWellDefined(BankingConstants.class);
    }

    private static void assertUtilityClassWellDefined(Class<?> clazz) throws Exception {
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
