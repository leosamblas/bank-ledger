package com.bankledger.auth.application.port.in;

import reactor.core.publisher.Mono;

public interface IntrospectTokenUseCase {
    Mono<IntrospectResult> introspect(IntrospectCommand command);
}
