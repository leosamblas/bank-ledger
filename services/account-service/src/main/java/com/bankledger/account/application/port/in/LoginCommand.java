package com.bankledger.account.application.port.in;

import java.util.Objects;

/**
 * Inbound command containing login credentials (CPF and raw password).
 */
public record LoginCommand(
        String cpf,
        String password
) {

    public LoginCommand {
        Objects.requireNonNull(cpf, "cpf must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }
}
