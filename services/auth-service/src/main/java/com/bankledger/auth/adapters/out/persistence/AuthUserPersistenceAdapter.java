package com.bankledger.auth.adapters.out.persistence;

import com.bankledger.auth.application.port.out.AuthUserRepositoryPort;
import com.bankledger.auth.domain.model.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthUserPersistenceAdapter implements AuthUserRepositoryPort {

    private final AuthUserRepository repository;

    @Override
    public Mono<AuthUser> findByCpf(String cpf) {
        return Mono.fromCallable(() -> repository.findByCpf(cpf).map(AuthUserEntity::toDomain))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<AuthUser> findByCustomerId(UUID customerId) {
        return Mono.fromCallable(() -> repository.findByCustomerId(customerId).map(AuthUserEntity::toDomain))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<AuthUser> findByRefreshToken(String refreshToken) {
        return Mono.fromCallable(() -> repository.findByRefreshToken(refreshToken).map(AuthUserEntity::toDomain))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<Boolean> existsByCpf(String cpf) {
        return Mono.fromCallable(() -> repository.existsByCpf(cpf))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<AuthUser> save(AuthUser authUser) {
        return Mono.fromCallable(() -> {
            AuthUserEntity entity = AuthUserEntity.fromDomain(authUser);
            AuthUserEntity saved = repository.save(entity);
            return saved.toDomain();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
