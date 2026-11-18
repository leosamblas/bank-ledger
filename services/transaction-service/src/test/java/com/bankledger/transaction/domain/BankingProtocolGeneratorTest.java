package com.bankledger.transaction.domain;

import com.bankledger.transaction.domain.service.BankingProtocolGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankingProtocolGeneratorTest {

    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^PRT-\\d{8}-[0-9A-F]{6}$");

    @Test
    @DisplayName("Should generate protocol adhering to PRT-YYYYMMDD-XXXXXX format with current date")
    void shouldGenerateProtocolInExpectedFormat() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        String protocol = generator.generate();

        assertThat(protocol).matches(PROTOCOL_PATTERN);
        assertThat(protocol).startsWith("PRT-");
        assertThat(protocol.length()).isEqualTo(19);
    }

    @Test
    @DisplayName("Should format date correctly when given a specific LocalDate")
    void shouldFormatGivenLocalDate() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        LocalDate fixedDate = LocalDate.of(2026, 11, 18);

        String protocol = generator.generate(fixedDate);

        assertThat(protocol).matches(PROTOCOL_PATTERN);
        assertThat(protocol).startsWith("PRT-20261118-");
    }

    @Test
    @DisplayName("Should respect fixed clock when generating without explicit date")
    void shouldRespectClock() {
        Instant fixedInstant = Instant.parse("2026-11-18T10:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("America/Sao_Paulo"));
        BankingProtocolGenerator generator = new BankingProtocolGenerator(fixedClock);

        String protocol = generator.generate();

        assertThat(protocol).startsWith("PRT-20261118-");
    }

    @Test
    @DisplayName("Should generate deterministic protocol when seeded with UUID")
    void shouldSupportSeededGeneration() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        LocalDate fixedDate = LocalDate.of(2026, 5, 4);
        java.util.UUID seed = java.util.UUID.fromString("12345678-abcd-1234-c000-000000000000");

        String protocol = generator.generate(fixedDate, seed);

        assertThat(protocol).isEqualTo("PRT-20260504-123456");
        assertThat(protocol).matches(PROTOCOL_PATTERN);
    }

    @Test
    @DisplayName("Should throw NullPointerException when date is null")
    void shouldThrowWhenDateIsNull() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        assertThatThrownBy(() -> generator.generate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("date must not be null");
    }

    @Test
    @DisplayName("Should generate distinct random hex suffixes across multiple invocations")
    void shouldGenerateDistinctSuffixes() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        LocalDate fixedDate = LocalDate.of(2026, 11, 18);
        Set<String> protocols = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            protocols.add(generator.generate(fixedDate));
        }

        // With 24 bits of entropy (16M values), 100 iterations should have 100 unique values
        assertThat(protocols).hasSize(100);
    }
}
