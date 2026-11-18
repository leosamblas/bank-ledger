package com.bankledger.account.adapters.in.rest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * REST response representing account and customer profile details.
 */
public record AccountResponse(
        UUID id,
        UUID customerId,
        String agency,
        String number,
        String digit,
        String status,
        Instant openedAt,
        String holderName,
        String cpf,
        String email,
        String phone,
        LocalDate birthDate,
        AddressDto address,
        Long dailyTransferLimitCents
) {
    public AccountResponse(
            UUID id,
            UUID customerId,
            String agency,
            String number,
            String digit,
            String status,
            Instant openedAt,
            String holderName,
            String cpf,
            String email,
            String phone,
            AddressDto address,
            Long dailyTransferLimitCents
    ) {
        this(id, customerId, agency, number, digit, status, openedAt, holderName, cpf, email, phone, null, address, dailyTransferLimitCents);
    }

    public AccountResponse(
            UUID id,
            UUID customerId,
            String agency,
            String number,
            String digit,
            String status,
            Instant openedAt,
            String holderName
    ) {
        this(id, customerId, agency, number, digit, status, openedAt, holderName, null, null, null, null, null, 5000000L);
    }

    public AccountResponse(
            UUID id,
            UUID customerId,
            String agency,
            String number,
            String digit,
            String status,
            Instant openedAt
    ) {
        this(id, customerId, agency, number, digit, status, openedAt, null, null, null, null, null, null, 5000000L);
    }

    public String holderCpf() {
        return cpf;
    }

    public String holderEmail() {
        return email;
    }

    public String holderPhone() {
        return phone;
    }
}
