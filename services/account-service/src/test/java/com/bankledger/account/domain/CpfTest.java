package com.bankledger.account.domain;

import com.bankledger.account.domain.exception.InvalidCpfException;
import com.bankledger.account.domain.vo.Cpf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @Test
    @DisplayName("Should create CPF with valid unmasked digits")
    void shouldCreateCpfWithValidUnmaskedDigits() {
        Cpf cpf = new Cpf("52998224725");
        assertThat(cpf.value()).isEqualTo("52998224725");
        assertThat(cpf.formatted()).isEqualTo("529.982.247-25");
    }

    @Test
    @DisplayName("Should create CPF with valid masked digits and normalize")
    void shouldCreateCpfWithValidMaskedDigits() {
        Cpf cpf = new Cpf("111.444.777-35");
        assertThat(cpf.value()).isEqualTo("11144477735");
        assertThat(cpf.formatted()).isEqualTo("111.444.777-35");
    }

    @Test
    @DisplayName("Should create CPF with leading zeros")
    void shouldCreateCpfWithLeadingZeros() {
        Cpf cpf = new Cpf("000.000.001-91");
        assertThat(cpf.value()).isEqualTo("00000000191");
        assertThat(cpf.formatted()).isEqualTo("000.000.001-91");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "00000000000",
        "11111111111",
        "22222222222",
        "33333333333",
        "44444444444",
        "55555555555",
        "66666666666",
        "77777777777",
        "88888888888",
        "99999999999"
    })
    @DisplayName("Should reject CPFs with all repeated digits")
    void shouldRejectRepeatedDigits(String repeatedCpf) {
        assertThatThrownBy(() -> new Cpf(repeatedCpf))
            .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    @DisplayName("Should reject CPF with invalid first check digit")
    void shouldRejectInvalidFirstCheckDigit() {
        assertThatThrownBy(() -> new Cpf("52998224735"))
            .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    @DisplayName("Should reject CPF with invalid second check digit")
    void shouldRejectInvalidSecondCheckDigit() {
        assertThatThrownBy(() -> new Cpf("52998224720"))
            .isInstanceOf(InvalidCpfException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "123",
        "1234567890",
        "123456789012",
        "abc12345678",
        "529.982.247-2X"
    })
    @DisplayName("Should reject CPFs with invalid lengths or non-numeric characters")
    void shouldRejectInvalidLengthsOrNonNumeric(String invalid) {
        assertThatThrownBy(() -> new Cpf(invalid))
            .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    @DisplayName("Should reject null CPF")
    void shouldRejectNullCpf() {
        assertThatThrownBy(() -> new Cpf(null))
            .isInstanceOf(InvalidCpfException.class);
    }
}
