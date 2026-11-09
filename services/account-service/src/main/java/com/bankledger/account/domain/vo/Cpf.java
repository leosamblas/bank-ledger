package com.bankledger.account.domain.vo;

import com.bankledger.account.domain.exception.InvalidCpfException;

import java.io.Serializable;

/**
 * Pure domain value object representing a Brazilian CPF (Cadastro de Pessoas Físicas).
 * Validates format, 11-digit length, identical-digit rejection, and checksum calculation.
 * Zero framework dependencies.
 */
public record Cpf(String value) implements Serializable {

    public Cpf {
        if (value == null || value.isBlank()) {
            throw new InvalidCpfException("CPF cannot be null or empty");
        }

        String trimmed = value.trim();
        if (!trimmed.matches("^[0-9.\\-]+$")) {
            throw new InvalidCpfException("CPF contains invalid characters: " + value);
        }

        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() != 11) {
            throw new InvalidCpfException("CPF must contain exactly 11 digits: " + value);
        }

        if (digits.chars().distinct().count() == 1) {
            throw new InvalidCpfException("CPF cannot consist of identical digits: " + value);
        }

        validateChecksum(digits, value);

        value = digits;
    }

    public static Cpf of(String value) {
        return new Cpf(value);
    }

    public String formatted() {
        return String.format(
                "%s.%s.%s-%s",
                value.substring(0, 3),
                value.substring(3, 6),
                value.substring(6, 9),
                value.substring(9, 11)
        );
    }

    private static void validateChecksum(String digits, String original) {
        // First check digit
        int sum1 = 0;
        for (int i = 0; i < 9; i++) {
            sum1 += Character.getNumericValue(digits.charAt(i)) * (10 - i);
        }
        int rem1 = sum1 % 11;
        int dv1 = (rem1 < 2) ? 0 : (11 - rem1);
        if (Character.getNumericValue(digits.charAt(9)) != dv1) {
            throw new InvalidCpfException("Invalid first check digit in CPF: " + original);
        }

        // Second check digit
        int sum2 = 0;
        for (int i = 0; i < 10; i++) {
            sum2 += Character.getNumericValue(digits.charAt(i)) * (11 - i);
        }
        int rem2 = sum2 % 11;
        int dv2 = (rem2 < 2) ? 0 : (11 - rem2);
        if (Character.getNumericValue(digits.charAt(10)) != dv2) {
            throw new InvalidCpfException("Invalid second check digit in CPF: " + original);
        }
    }

    @Override
    public String toString() {
        return formatted();
    }
}
