package com.bankledger.auth.application.port.in;

public record LoginCommand(
        String cpf,
        String password
) {
    public LoginCommand {
        if (cpf != null) {
            cpf = cpf.replaceAll("\\D", "");
        }
    }
}
