package com.bankledger.account.domain;

import com.bankledger.account.domain.exception.InvalidAccountNumberException;
import com.bankledger.account.domain.vo.AccountNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNumberTest {

    @ParameterizedTest
    @CsvSource({
        "100000, 4",
        "100001, 2",
        "100002, 0",
        "100011, 0",
        "000002, 7",
        "654321, 9",
        "123456, 0"
    })
    @DisplayName("Should correctly calculate check digit using Modulo 11")
    void shouldCalculateCheckDigitWithModulo11(String number, String expectedCheckDigit) {
        String calculatedDv = AccountNumber.calculateCheckDigit(number);
        assertThat(calculatedDv).isEqualTo(expectedCheckDigit);

        AccountNumber account = AccountNumber.of(number);
        assertThat(account.number()).isEqualTo(number);
        assertThat(account.checkDigit()).isEqualTo(expectedCheckDigit);
        assertThat(account.branch()).isEqualTo("0001");
        assertThat(account.formatted()).isEqualTo(number + "-" + expectedCheckDigit);
    }

    @Test
    @DisplayName("Should accept valid explicit check digit")
    void shouldAcceptValidExplicitCheckDigit() {
        AccountNumber account = new AccountNumber("100000", "4");
        assertThat(account.number()).isEqualTo("100000");
        assertThat(account.checkDigit()).isEqualTo("4");
        assertThat(account.formatted()).isEqualTo("100000-4");
    }

    @Test
    @DisplayName("Should reject mismatched check digit")
    void shouldRejectMismatchedCheckDigit() {
        assertThatThrownBy(() -> new AccountNumber("100000", "9"))
            .isInstanceOf(InvalidAccountNumberException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "12345",
        "1234567",
        "10000A",
        "ABCDEF"
    })
    @DisplayName("Should reject account numbers that are not exactly 6 digits")
    void shouldRejectInvalidNumberLengths(String invalidNumber) {
        assertThatThrownBy(() -> AccountNumber.of(invalidNumber))
            .isInstanceOf(InvalidAccountNumberException.class);
    }

    @Test
    @DisplayName("Should reject null number")
    void shouldRejectNullNumber() {
        assertThatThrownBy(() -> AccountNumber.of(null))
            .isInstanceOf(InvalidAccountNumberException.class);
    }

    @Test
    @DisplayName("Should parse formatted account numbers")
    void shouldParseFormattedAccountNumbers() {
        AccountNumber acc1 = AccountNumber.parse("100000-4");
        assertThat(acc1.number()).isEqualTo("100000");
        assertThat(acc1.checkDigit()).isEqualTo("4");
        assertThat(acc1.branch()).isEqualTo("0001");

        AccountNumber acc2 = AccountNumber.parse("0002/000002-7");
        assertThat(acc2.number()).isEqualTo("000002");
        assertThat(acc2.checkDigit()).isEqualTo("7");
        assertThat(acc2.branch()).isEqualTo("0002");
    }

    @Test
    @DisplayName("Should reject invalid branch format")
    void shouldRejectInvalidBranch() {
        assertThatThrownBy(() -> new AccountNumber("12", "100000", "4"))
            .isInstanceOf(InvalidAccountNumberException.class);
    }
}
