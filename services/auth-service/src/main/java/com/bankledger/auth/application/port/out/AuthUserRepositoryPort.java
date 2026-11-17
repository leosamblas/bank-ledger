package com.bankledger.auth.application.port.out;

import com.bankledger.auth.domain.model.AuthUser;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AuthUserRepositoryPort {
    Mono<AuthUser> findByCpf(String cpf);
    Mono<AuthUser> findByCustomerId(UUID customerId);
    Mono<AuthUser> findByRefreshToken(String refreshToken);
    Mono<Boolean> existsByCpf(String cpf);
    Mono<AuthUser> save(AuthUser authUser);
}
