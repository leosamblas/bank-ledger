package com.bankledger.transaction.domain;

import com.bankledger.transaction.domain.service.BankingProtocolGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankingProtocolGeneratorTest {

    @Test
    @DisplayName("Should generate protocol adhering to valid canonical UUID format")
    void shouldGenerateProtocolInExpectedFormat() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        String protocol = generator.generate();

        assertThat(protocol).isNotNull();
        assertThat(UUID.fromString(protocol)).isNotNull();
        assertThat(protocol.length()).isEqualTo(36);
    }

    @Test
    @DisplayName("Should format UUID correctly when given LocalDate or Clock")
    void shouldFormatGivenLocalDate() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        LocalDate fixedDate = LocalDate.of(2026, 11, 18);

        String protocol = generator.generate(fixedDate);

        assertThat(protocol).isNotNull();
        assertThat(UUID.fromString(protocol)).isNotNull();
    }

    @Test
    @DisplayName("Should support seeded UUID generation deterministically")
    void shouldSupportSeededGeneration() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        UUID seed = UUID.fromString("12345678-abcd-1234-c000-000000000000");

        String protocol = generator.generate(seed);

        assertThat(protocol).isEqualTo("12345678-abcd-1234-c000-000000000000");
    }

    @Test
    @DisplayName("Should support date and seed overload")
    void shouldSupportDateAndSeedOverload() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        LocalDate fixedDate = LocalDate.of(2026, 5, 4);
        UUID seed = UUID.fromString("12345678-abcd-1234-c000-000000000000");

        String protocol = generator.generate(fixedDate, seed);

        assertThat(protocol).isEqualTo("12345678-abcd-1234-c000-000000000000");
    }

    @Test
    @DisplayName("Should generate distinct random UUIDs across multiple invocations")
    void shouldGenerateDistinctUuids() {
        BankingProtocolGenerator generator = new BankingProtocolGenerator();
        Set<String> protocols = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            protocols.add(generator.generate());
        }

        assertThat(protocols).hasSize(100);
    }
}
