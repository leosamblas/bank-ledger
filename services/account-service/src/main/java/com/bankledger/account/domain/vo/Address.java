package com.bankledger.account.domain.vo;

import com.bankledger.account.domain.exception.InvalidAddressException;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * Pure domain value object representing a Customer Address.
 * Immutable record with validation: postalCode, street, number, complement, neighborhood, city, state.
 * Zero framework dependencies.
 */
public record Address(
        String postalCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state
) implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Set<String> BRAZILIAN_STATES = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    );

    public Address {
        if (postalCode == null || postalCode.isBlank()) {
            throw new InvalidAddressException("Postal code cannot be null or empty");
        }
        String cleanCep = postalCode.trim().replaceAll("\\D", "");
        if (cleanCep.length() != 8) {
            throw new InvalidAddressException("Postal code must have exactly 8 digits: " + postalCode);
        }

        if (street == null || street.isBlank()) {
            throw new InvalidAddressException("Street cannot be null or empty");
        }
        if (street.trim().length() > 150) {
            throw new InvalidAddressException("Street cannot exceed 150 characters");
        }

        if (number == null || number.isBlank()) {
            throw new InvalidAddressException("Number cannot be null or empty");
        }
        if (number.trim().length() > 20) {
            throw new InvalidAddressException("Number cannot exceed 20 characters");
        }

        if (complement != null && complement.trim().length() > 100) {
            throw new InvalidAddressException("Complement cannot exceed 100 characters");
        }

        if (neighborhood == null || neighborhood.isBlank()) {
            throw new InvalidAddressException("Neighborhood cannot be null or empty");
        }
        if (neighborhood.trim().length() > 100) {
            throw new InvalidAddressException("Neighborhood cannot exceed 100 characters");
        }

        if (city == null || city.isBlank()) {
            throw new InvalidAddressException("City cannot be null or empty");
        }
        if (city.trim().length() > 100) {
            throw new InvalidAddressException("City cannot exceed 100 characters");
        }

        if (state == null || state.isBlank()) {
            throw new InvalidAddressException("State cannot be null or empty");
        }
        String upperState = state.trim().toUpperCase();
        if (!BRAZILIAN_STATES.contains(upperState)) {
            throw new InvalidAddressException("Invalid Brazilian state (UF): " + state);
        }

        postalCode = String.format("%s-%s", cleanCep.substring(0, 5), cleanCep.substring(5));
        street = street.trim();
        number = number.trim();
        complement = (complement == null || complement.isBlank()) ? null : complement.trim();
        neighborhood = neighborhood.trim();
        city = city.trim();
        state = upperState;
    }

    public static Address of(
            String postalCode,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state
    ) {
        return new Address(postalCode, street, number, complement, neighborhood, city, state);
    }
}
