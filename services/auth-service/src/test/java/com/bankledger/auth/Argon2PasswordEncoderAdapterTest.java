package com.bankledger.auth;

import com.bankledger.auth.adapters.out.security.Argon2PasswordEncoderAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Argon2PasswordEncoderAdapterTest {

    private final Argon2PasswordEncoderAdapter adapter = new Argon2PasswordEncoderAdapter();

    @Test
    @DisplayName("Should encode raw password using Argon2id format")
    void shouldEncodePasswordWithArgon2id() {
        String rawPassword = "SecurePassword@123";
        String encoded = adapter.encode(rawPassword);

        assertThat(encoded).isNotEmpty();
        assertThat(encoded).startsWith("$argon2id$");
    }

    @Test
    @DisplayName("Should match correct raw password against encoded hash")
    void shouldMatchCorrectPassword() {
        String rawPassword = "SecurePassword@123";
        String encoded = adapter.encode(rawPassword);

        boolean matches = adapter.matches(rawPassword, encoded);
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should reject incorrect raw password")
    void shouldRejectIncorrectPassword() {
        String rawPassword = "SecurePassword@123";
        String encoded = adapter.encode(rawPassword);

        boolean matches = adapter.matches("WrongPassword@999", encoded);
        assertThat(matches).isFalse();
    }
}
