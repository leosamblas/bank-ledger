package com.bankledger.transaction.domain.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain service responsible for generating banking audit protocol numbers using UUIDs.
 * Simple, robust, and zero-complexity implementation.
 */
public class BankingProtocolGenerator {

    public BankingProtocolGenerator() {
    }

    public BankingProtocolGenerator(Clock clock) {
        // Kept for backward compatibility in constructor injection and existing tests
    }

    /**
     * Generates a unique banking protocol number as a standard UUID string.
     */
    public String generate() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a protocol number using an optional seed UUID, or creates a new random UUID if seed is null.
     *
     * @param seed Optional UUID seed (e.g. transactionId)
     * @return Formatted UUID protocol string
     */
    public String generate(UUID seed) {
        return (seed != null ? seed : UUID.randomUUID()).toString();
    }

    /**
     * Legacy overload for backward compatibility with date-based callers.
     */
    public String generate(LocalDate date) {
        return generate();
    }

    /**
     * Legacy overload for backward compatibility with date-and-seed callers.
     */
    public String generate(LocalDate date, UUID seed) {
        return generate(seed);
    }
}
