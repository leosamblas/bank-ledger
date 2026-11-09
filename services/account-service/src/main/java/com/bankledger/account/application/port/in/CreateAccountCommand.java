package com.bankledger.account.application.port.in;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Inbound command containing data required to open a new bank account.
 */
public record CreateAccountCommand(
        String fullName,
        String cpf,
        LocalDate birthDate,
        String email,
        String phone,
        String password
) {

    public CreateAccountCommand {
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(cpf, "cpf must not be null");
        Objects.requireNonNull(birthDate, "birthDate must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }
}
