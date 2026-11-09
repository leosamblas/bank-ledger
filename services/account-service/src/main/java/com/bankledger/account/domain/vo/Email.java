package com.bankledger.account.domain.vo;

import com.bankledger.account.domain.exception.InvalidEmailException;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * Pure domain value object representing an Email address.
 * Normalizes input by trimming and lowercasing.
 * Zero framework dependencies.
 */
public record Email(String value) implements Serializable {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public Email {
        if (value == null || value.isBlank()) {
            throw new InvalidEmailException("Email cannot be null or empty");
        }

        String normalized = value.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidEmailException("Invalid email format: " + value);
        }

        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
