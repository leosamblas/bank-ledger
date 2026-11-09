package com.bankledger.account.application.port.out;

import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;

import java.util.Optional;
import java.util.UUID;

/**
 * Outgoing repository port for persisting and querying Customer aggregates.
 * Defined in application layer, implemented by persistence adapter.
 */
public interface CustomerRepositoryPort {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByCpf(Cpf cpf);

    Optional<Customer> findByEmail(Email email);

    boolean existsByCpf(Cpf cpf);

    boolean existsByEmail(Email email);
}
