package com.bankledger.account.adapters.in.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for PUT /api/v1/accounts/me/profile.
 * Allows updating contact and address information while keeping identity immutable.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Phone cannot be blank")
        String phone,

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email must be valid")
        String email,

        String postalCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        AddressDto address
) {
    public UpdateProfileRequest(
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
        this(phone, email, postalCode, street, number, complement, neighborhood, city, state, null);
    }

    public String effectivePostalCode() {
        return address != null && address.postalCode() != null ? address.postalCode() : postalCode;
    }

    public String effectiveStreet() {
        return address != null && address.street() != null ? address.street() : street;
    }

    public String effectiveNumber() {
        return address != null && address.number() != null ? address.number() : number;
    }

    public String effectiveComplement() {
        return address != null && address.complement() != null ? address.complement() : complement;
    }

    public String effectiveNeighborhood() {
        return address != null && address.neighborhood() != null ? address.neighborhood() : neighborhood;
    }

    public String effectiveCity() {
        return address != null && address.city() != null ? address.city() : city;
    }

    public String effectiveState() {
        return address != null && address.state() != null ? address.state() : state;
    }
}
