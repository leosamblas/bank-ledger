package com.bankledger.auth.application.port.in;

import reactor.core.publisher.Mono;

public interface LoginUseCase {
    Mono<LoginResult> login(LoginCommand command);
}
