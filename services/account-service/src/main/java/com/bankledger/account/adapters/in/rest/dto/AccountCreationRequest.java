package com.bankledger.account.adapters.in.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AccountCreationRequest(
        @NotBlank(message = "fullName must not be blank")
        String fullName,

        @NotBlank(message = "cpf must not be blank")
        String cpf,

        @NotNull(message = "birthDate must not be null")
        LocalDate birthDate,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be valid")
        String email,

        @NotBlank(message = "phone must not be blank")
        String phone,

        @NotBlank(message = "password must not be blank")
        String password
) {
}

