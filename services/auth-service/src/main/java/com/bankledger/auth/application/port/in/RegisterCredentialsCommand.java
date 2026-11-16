package com.bankledger.auth.application.port.in;

import com.bankledger.commons.security.dto.RegisterCredentialsPayload;

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

    public static RegisterCredentialsCommand fromPayload(RegisterCredentialsPayload payload) {
        return new RegisterCredentialsCommand(
                payload.customerId(),
                payload.cpf(),
                payload.password(),
                payload.accountId(),
                payload.branch(),
                payload.accountNumber(),
                payload.fullName(),
                payload.email()
        );
    }

    public RegisterCredentialsPayload toPayload() {
        return new RegisterCredentialsPayload(
                customerId,
                cpf,
                password,
                accountId,
                branch,
                accountNumber,
                fullName,
                email
        );
    }
}
