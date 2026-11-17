package com.bankledger.auth.application.port.in;

import com.bankledger.auth.domain.model.AuthUser;
import reactor.core.publisher.Mono;

public interface RegisterCredentialsUseCase {
    Mono<AuthUser> registerCredentials(RegisterCredentialsCommand command);
}
