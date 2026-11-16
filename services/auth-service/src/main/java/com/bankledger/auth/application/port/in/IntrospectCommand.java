package com.bankledger.auth.application.port.in;

public record IntrospectCommand(
        String token
) {}
