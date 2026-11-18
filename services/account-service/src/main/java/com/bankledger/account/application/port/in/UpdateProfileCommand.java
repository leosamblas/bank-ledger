package com.bankledger.account.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Command for updating customer profile (phone, email, address).
 */
public record UpdateProfileCommand(
        UUID accountId,
        String phone,
        String email,
        String postalCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state
) {
    public UpdateProfileCommand {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(postalCode, "postalCode must not be null");
        Objects.requireNonNull(street, "street must not be null");
        Objects.requireNonNull(number, "number must not be null");
        Objects.requireNonNull(neighborhood, "neighborhood must not be null");
        Objects.requireNonNull(city, "city must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }
}
