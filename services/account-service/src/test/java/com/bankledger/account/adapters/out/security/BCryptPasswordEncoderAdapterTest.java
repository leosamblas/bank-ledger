package com.bankledger.account.adapters.out.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCryptPasswordEncoderAdapterTest {

    private BCryptPasswordEncoderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BCryptPasswordEncoderAdapter();
    }

    @Test
    @DisplayName("encode() generates non-null BCrypt hash distinct from raw password")
    void shouldEncodePasswordSuccessfully() {
        String raw = "StrongPass@123";
        String encoded = adapter.encode(raw);

        assertThat(encoded).isNotBlank();
        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoded).startsWith("$2a$");
    }

    @Test
    @DisplayName("matches() returns true when raw password matches hash")
    void shouldMatchCorrectPassword() {
        String raw = "StrongPass@123";
        String encoded = adapter.encode(raw);

        assertThat(adapter.matches(raw, encoded)).isTrue();
    }

    @Test
    @DisplayName("matches() returns false when raw password differs from hash")
    void shouldNotMatchIncorrectPassword() {
        String raw = "StrongPass@123";
        String encoded = adapter.encode(raw);

        assertThat(adapter.matches("WrongPass@999", encoded)).isFalse();
    }
}

