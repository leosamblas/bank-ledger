package com.bankledger.auth.application.port.out;

import com.bankledger.auth.domain.model.AuthUser;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepositoryPort {
    Optional<AuthUser> findByCpf(String cpf);
    Optional<AuthUser> findByCustomerId(UUID customerId);
    boolean existsByCpf(String cpf);
    AuthUser save(AuthUser authUser);
}
