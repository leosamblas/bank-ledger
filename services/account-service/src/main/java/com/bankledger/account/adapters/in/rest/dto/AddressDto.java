package com.bankledger.account.adapters.in.rest.dto;

import com.bankledger.account.domain.vo.Address;

/**
 * REST DTO representing address for customer responses and requests.
 */
public record AddressDto(
        String postalCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state
) {
    public static AddressDto fromDomain(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressDto(
                address.postalCode(),
                address.street(),
                address.number(),
                address.complement(),
                address.neighborhood(),
                address.city(),
                address.state()
        );
    }
}
