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
    @DisplayName("encode() gera hash BCrypt não nulo e diferente da senha original")
    void shouldEncodePasswordSuccessfully() {
        String raw = "StrongPass@123";
        String encoded = adapter.encode(raw);

        assertThat(encoded).isNotBlank();
        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoded).startsWith("$2a$");
    }

    @Test
    @DisplayName("matches() retorna true quando a senha corresponde ao hash")
    void shouldMatchCorrectPassword() {
        String raw = "StrongPass@123";
        String encoded = adapter.encode(raw);

        assertThat(adapter.matches(raw, encoded)).isTrue();
    }

    @Test
    @DisplayName("matches() retorna false quando a senha é diferente do hash")
    void shouldNotMatchIncorrectPassword() {
        String raw = "StrongPass@123";
        String encoded = adapter.encode(raw);

        assertThat(adapter.matches("WrongPass@999", encoded)).isFalse();
    }
}

