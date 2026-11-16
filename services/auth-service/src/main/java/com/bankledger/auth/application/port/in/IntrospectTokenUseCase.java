package com.bankledger.auth.application.port.in;

public interface IntrospectTokenUseCase {
    IntrospectResult introspect(IntrospectCommand command);
}
