package com.bankledger.auth.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, UUID> {
    Optional<AuthUserEntity> findByCpf(String cpf);
    Optional<AuthUserEntity> findByCustomerId(UUID customerId);
    boolean existsByCpf(String cpf);
}
