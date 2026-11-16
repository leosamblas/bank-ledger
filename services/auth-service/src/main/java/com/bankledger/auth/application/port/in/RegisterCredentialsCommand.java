package com.bankledger.auth.application.port.in;

import java.util.UUID;

public record RegisterCredentialsCommand(
        UUID customerId,
        String cpf,
        String password,
        UUID accountId,
        String branch,
        String accountNumber,
        String fullName,
        String email
) {
    public RegisterCredentialsCommand {
        if (cpf != null) {
            cpf = cpf.replaceAll("\\D", "");
        }
    }
}
