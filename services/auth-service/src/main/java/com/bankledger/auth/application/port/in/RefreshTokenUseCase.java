package com.bankledger.auth.application.port.in;

import reactor.core.publisher.Mono;

public interface RefreshTokenUseCase {
    Mono<LoginResult> refreshToken(RefreshTokenCommand command);
}
