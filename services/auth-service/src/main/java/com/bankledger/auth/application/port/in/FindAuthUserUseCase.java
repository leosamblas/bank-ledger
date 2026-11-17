package com.bankledger.auth.application.port.in;

import com.bankledger.auth.domain.model.AuthUser;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FindAuthUserUseCase {
    Mono<AuthUser> findByCustomerId(UUID customerId);
    Mono<AuthUser> findByCpf(String cpf);
}
