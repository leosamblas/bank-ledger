package com.bankledger.account.domain.vo;

import com.bankledger.account.domain.exception.InvalidAccountNumberException;
import com.bankledger.account.domain.model.Account;

import java.io.Serializable;
import java.util.Objects;

/**
 * Pure domain value object representing a Bank Account Number.
 * Consists of a 4-digit branch (default '0001'), a 6-digit account number,
 * and a check digit calculated via Modulo 11 (weights 2 to 9, right to left).
 * Zero framework dependencies.
 */
public record AccountNumber(
        String branch,
        String number,
        String checkDigit
) implements Serializable {

    public static final String DEFAULT_BRANCH = Account.DEFAULT_BRANCH;

    public AccountNumber {
        if (branch == null || branch.isBlank()) {
            branch = DEFAULT_BRANCH;
        } else {
            branch = branch.trim();
            if (!branch.matches("^\\d{4}$")) {
                throw new InvalidAccountNumberException("Branch must be exactly 4 digits: " + branch);
            }
        }

        if (number == null || number.isBlank()) {
            throw new InvalidAccountNumberException("Account number cannot be null or empty");
        }

        number = number.trim();
        if (!number.matches("^\\d{6}$")) {
            throw new InvalidAccountNumberException("Account number must be exactly 6 digits: " + number);
        }

        String calculatedDv = calculateCheckDigit(number);
        if (checkDigit == null || checkDigit.isBlank()) {
            checkDigit = calculatedDv;
        } else {
            checkDigit = checkDigit.trim();
            if (!checkDigit.equals(calculatedDv)) {
                throw new InvalidAccountNumberException(
                        String.format("Invalid check digit '%s' for account number '%s'. Expected '%s'",
                                checkDigit, number, calculatedDv)
                );
            }
        }
    }

    public AccountNumber(String number, String checkDigit) {
        this(DEFAULT_BRANCH, number, checkDigit);
    }

    public AccountNumber(String number) {
        this(DEFAULT_BRANCH, number, null);
    }

    public static AccountNumber of(String number) {
        return new AccountNumber(number);
    }

    public static AccountNumber of(String number, String checkDigit) {
        return new AccountNumber(number, checkDigit);
    }

    public static AccountNumber of(String branch, String number, String checkDigit) {
        return new AccountNumber(branch, number, checkDigit);
    }

    public static AccountNumber ofBranchAndNumber(String branch, String number) {
        return new AccountNumber(branch, number, null);
    }

    /**
     * Parses account number from formatted representations:
     * - "100000-4"
     * - "0001/100000-4"
     * - "100000" (DV will be calculated)
     */
    public static AccountNumber parse(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidAccountNumberException("Input to parse cannot be null or empty");
        }
        String clean = input.trim();

        String branch = DEFAULT_BRANCH;
        if (clean.contains("/")) {
            String[] branchParts = clean.split("/", 2);
            branch = branchParts[0].trim();
            clean = branchParts[1].trim();
        }

        if (clean.contains("-")) {
            String[] parts = clean.split("-", 2);
            return new AccountNumber(branch, parts[0].trim(), parts[1].trim());
        }

        if (clean.length() == 7 && clean.matches("^\\d{7}$")) {
            return new AccountNumber(branch, clean.substring(0, 6), clean.substring(6, 7));
        }

        return new AccountNumber(branch, clean, null);
    }

    /**
     * Calculates check digit using Modulo 11 (weights 2 to 9, right to left).
     * Modulo 11 standard: sum products, remainder = sum % 11;
     * if 11 - remainder is 10 or 11, DV is "0", otherwise String.valueOf(11 - remainder).
     */
    public static String calculateCheckDigit(String number) {
        if (number == null || !number.matches("^\\d+$")) {
            throw new InvalidAccountNumberException("Account number must contain only digits: " + number);
        }

        int weight = 2;
        int sum = 0;
        for (int i = number.length() - 1; i >= 0; i--) {
            sum += Character.getNumericValue(number.charAt(i)) * weight;
            weight++;
            if (weight > 9) {
                weight = 2;
            }
        }

        int remainder = sum % 11;
        int diff = 11 - remainder;
        if (diff == 10 || diff == 11) {
            return "0";
        }
        return String.valueOf(diff);
    }

    public String formatted() {
        return number + "-" + checkDigit;
    }

    public String fullFormatted() {
        return branch + "/" + formatted();
    }

    @Override
    public String toString() {
        return formatted();
    }
}
