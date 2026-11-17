package com.bankledger.auth;

import com.bankledger.auth.adapters.out.security.BCryptPasswordEncoderAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordEncoderAdapterTest {

    private final BCryptPasswordEncoderAdapter adapter = new BCryptPasswordEncoderAdapter();

    @Test
    @DisplayName("Should encode raw password using BCrypt format with cost factor 10")
    void shouldEncodePasswordWithBCrypt10() {
        String rawPassword = "SecurePassword@123";
        String encoded = adapter.encode(rawPassword);

        assertThat(encoded).isNotEmpty();
        assertThat(encoded).startsWith("$2a$10$");
    }

    @Test
    @DisplayName("Should match correct raw password against BCrypt encoded hash")
    void shouldMatchCorrectPassword() {
        String rawPassword = "SecurePassword@123";
        String encoded = adapter.encode(rawPassword);

        boolean matches = adapter.matches(rawPassword, encoded);
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should reject incorrect raw password against BCrypt hash")
    void shouldRejectIncorrectPassword() {
        String rawPassword = "SecurePassword@123";
        String encoded = adapter.encode(rawPassword);

        boolean matches = adapter.matches("WrongPassword@999", encoded);
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should return false when encoded password is null")
    void shouldReturnFalseWhenEncodedPasswordIsNull() {
        boolean matches = adapter.matches("AnyPassword", null);
        assertThat(matches).isFalse();

        boolean upgrade = adapter.upgradeEncoding(null);
        assertThat(upgrade).isFalse();
    }
}
