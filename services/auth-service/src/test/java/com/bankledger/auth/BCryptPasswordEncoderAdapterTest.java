package com.bankledger.auth;

import com.bankledger.auth.adapters.out.security.BCryptPasswordEncoderAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

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
    @DisplayName("Should match legacy Argon2id encoded hash for backward compatibility")
    void shouldMatchLegacyArgon2idHash() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19456, 3);
        String rawPassword = "LegacyPassword@2026";
        String legacyHash = argon2.encode(rawPassword);

        assertThat(legacyHash).startsWith("$argon2id$");
        assertThat(adapter.matches(rawPassword, legacyHash)).isTrue();
        assertThat(adapter.matches("WrongPassword", legacyHash)).isFalse();
    }
}
