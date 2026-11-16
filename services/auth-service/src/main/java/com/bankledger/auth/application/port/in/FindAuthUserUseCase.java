package com.bankledger.auth.application.port.in;

import com.bankledger.auth.domain.model.AuthUser;

import java.util.Optional;
import java.util.UUID;

public interface FindAuthUserUseCase {
    Optional<AuthUser> findByCustomerId(UUID customerId);
    Optional<AuthUser> findByCpf(String cpf);
}
