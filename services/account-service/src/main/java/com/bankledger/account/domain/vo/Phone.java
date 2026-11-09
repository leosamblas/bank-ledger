package com.bankledger.account.domain.vo;

import com.bankledger.account.domain.exception.InvalidPhoneException;

import java.io.Serializable;

/**
 * Pure domain value object representing a Phone number (10 to 15 digits / E.164).
 * Strips masks like spaces, hyphens, parentheses while preserving E.164 international prefix '+'.
 * Zero framework dependencies.
 */
public record Phone(String value) implements Serializable {

    public Phone {
        if (value == null || value.isBlank()) {
            throw new InvalidPhoneException("Phone cannot be null or empty");
        }

        String trimmed = value.trim();

        // Must only contain digits, optional leading +, spaces, hyphens, dots, parentheses
        if (!trimmed.matches("^\\+?[0-9()\\-.\\s]+$")) {
            throw new InvalidPhoneException("Phone contains invalid characters: " + value);
        }

        // Clean formatting
        String cleaned = trimmed.replaceAll("[^0-9+]", "");

        // + can only be at index 0 and cannot be the only character
        if (cleaned.contains("+") && (!cleaned.startsWith("+") || cleaned.lastIndexOf('+') > 0 || cleaned.length() == 1)) {
            throw new InvalidPhoneException("Invalid phone format: " + value);
        }

        String digitsOnly = cleaned.startsWith("+") ? cleaned.substring(1) : cleaned;
        if (digitsOnly.length() < 10 || digitsOnly.length() > 15) {
            throw new InvalidPhoneException(
                    String.format("Phone must contain between 10 and 15 digits, found %d: %s",
                            digitsOnly.length(), value)
            );
        }

        value = cleaned;
    }

    public static Phone of(String value) {
        return new Phone(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
