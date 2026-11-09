package com.bankledger.account.domain;

import com.bankledger.account.domain.exception.InvalidPhoneException;
import com.bankledger.account.domain.vo.Phone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneTest {

    @Test
    @DisplayName("Should create phone with valid digits and normalize")
    void shouldCreateValidPhone() {
        Phone phone1 = new Phone("11987654321");
        assertThat(phone1.value()).isEqualTo("11987654321");

        Phone phone2 = new Phone("(11) 98765-4321");
        assertThat(phone2.value()).isEqualTo("11987654321");

        Phone phone3 = new Phone("+5511987654321");
        assertThat(phone3.value()).isEqualTo("+5511987654321");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "12345",
        "123456789",
        "1234567890123456",
        "phone12345678",
        "+55+11987654321"
    })
    @DisplayName("Should reject invalid phone lengths and non-numeric content")
    void shouldRejectInvalidPhone(String invalid) {
        assertThatThrownBy(() -> new Phone(invalid))
            .isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    @DisplayName("Should reject null phone")
    void shouldRejectNullPhone() {
        assertThatThrownBy(() -> new Phone(null))
            .isInstanceOf(InvalidPhoneException.class);
    }
}
