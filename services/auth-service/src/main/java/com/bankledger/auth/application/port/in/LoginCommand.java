package com.bankledger.auth.application.port.in;

import java.util.UUID;

public record LoginCommand(
        String cpf,
        String password
) {}
