package com.bankledger.auth.adapters.out.persistence;

import com.bankledger.auth.application.port.out.AuthUserRepositoryPort;
import com.bankledger.auth.domain.model.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthUserPersistenceAdapter implements AuthUserRepositoryPort {

    private final AuthUserRepository repository;

    @Override
    public Optional<AuthUser> findByCpf(String cpf) {
        return repository.findByCpf(cpf).map(AuthUserEntity::toDomain);
    }

    @Override
    public Optional<AuthUser> findByCustomerId(UUID customerId) {
        return repository.findByCustomerId(customerId).map(AuthUserEntity::toDomain);
    }

    @Override
    public Optional<AuthUser> findByRefreshToken(String refreshToken) {
        return repository.findByRefreshToken(refreshToken).map(AuthUserEntity::toDomain);
    }

    @Override
    public boolean existsByCpf(String cpf) {
        return repository.existsByCpf(cpf);
    }

    @Override
    public AuthUser save(AuthUser authUser) {
        AuthUserEntity entity = AuthUserEntity.fromDomain(authUser);
        AuthUserEntity saved = repository.save(entity);
        return saved.toDomain();
    }
}
