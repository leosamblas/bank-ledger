package com.bankledger.transaction.domain.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain service responsible for generating canonical banking audit protocol numbers.
 * Format: PRT-YYYYMMDD-XXXXXX (e.g. PRT-20261118-9F8A2D)
 * Pure domain logic: no framework dependencies.
 */
public class BankingProtocolGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    public BankingProtocolGenerator() {
        this(Clock.system(DEFAULT_ZONE));
    }

    public BankingProtocolGenerator(Clock clock) {
        this.clock = clock != null ? clock : Clock.system(DEFAULT_ZONE);
    }

    /**
     * Generates a protocol number using the current date in America/Sao_Paulo.
     */
    public String generate() {
        return generate(LocalDate.now(clock));
    }

    /**
     * Generates a protocol number for a specified date.
     *
     * @param date Date for the protocol timestamp
     * @return Formatted protocol string
     */
    public String generate(LocalDate date) {
        return generate(date, null);
    }

    /**
     * Generates a protocol number for a specified date, optionally seeded with a unique UUID
     * to prevent concurrency collisions and enable deterministic cross-service correlation.
     *
     * @param date Date for the protocol timestamp
     * @param seed Optional UUID seed (e.g. transactionId)
     * @return Formatted protocol string
     */
    public String generate(LocalDate date, UUID seed) {
        Objects.requireNonNull(date, "date must not be null");
        String suffix;
        if (seed != null) {
            suffix = seed.toString().replace("-", "").substring(0, 6).toUpperCase();
        } else {
            int randomVal = RANDOM.nextInt(0x1000000); // 24 bits -> 6 hex chars
            suffix = String.format("%06X", randomVal);
        }
        return String.format("PRT-%s-%s", date.format(DATE_FORMATTER), suffix);
    }
}
