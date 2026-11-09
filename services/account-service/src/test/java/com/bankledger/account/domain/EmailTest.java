package com.bankledger.account.domain;

import com.bankledger.account.domain.exception.InvalidEmailException;
import com.bankledger.account.domain.vo.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    @DisplayName("Should create valid email and normalize by trimming and lowercasing")
    void shouldCreateAndNormalizeValidEmail() {
        Email email = new Email("   User.Name@Domain.COM   ");
        assertThat(email.value()).isEqualTo("user.name@domain.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "invalid-email",
        "@missing-user.com",
        "user@.com",
        "user@domain",
        "user@domain."
    })
    @DisplayName("Should reject invalid email formats")
    void shouldRejectInvalidEmail(String invalid) {
        assertThatThrownBy(() -> new Email(invalid))
            .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    @DisplayName("Should reject null email")
    void shouldRejectNullEmail() {
        assertThatThrownBy(() -> new Email(null))
            .isInstanceOf(InvalidEmailException.class);
    }
}
