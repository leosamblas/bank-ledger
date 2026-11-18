package com.bankledger.transaction.domain.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

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
        Objects.requireNonNull(date, "date must not be null");
        int randomVal = RANDOM.nextInt(0x1000000); // 24 bits -> 6 hex chars
        return String.format("PRT-%s-%06X", date.format(DATE_FORMATTER), randomVal);
    }

    /**
     * Convenience alias for generate().
     */
    public String generateProtocol() {
        return generate();
    }

    /**
     * Convenience alias for generate(date).
     */
    public String generateProtocol(LocalDate date) {
        return generate(date);
    }
}
