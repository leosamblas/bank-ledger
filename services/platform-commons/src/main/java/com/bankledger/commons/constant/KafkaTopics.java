package com.bankledger.commons.constant;

/**
 * Centralized Kafka topic names and suffixes for the bank-ledger ecosystem.
 */
public final class KafkaTopics {

    public static final String ACCOUNT_EVENTS_V1 = "account.events.v1";
    public static final String LEDGER_COMMANDS_V1 = "ledger.commands.v1";
    public static final String LEDGER_JOURNAL_RESULTS_V1 = "ledger.journal-results.v1";
    public static final String LEDGER_ACCOUNT_EVENTS_V1 = "ledger.account-events.v1";
    public static final String LEDGER_ENTRIES_V1 = "ledger.entries.v1";
    public static final String TRANSACTION_EVENTS_V1 = "transaction.events.v1";
    public static final String DEAD_LETTER_TOPIC_SUFFIX = ".dlt";

    private KafkaTopics() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
