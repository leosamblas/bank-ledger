package com.bankledger.account.adapters.out.persistence;

import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter implementing CustomerRepositoryPort using Spring Data JPA.
 */
@Repository
public class CustomerPersistenceAdapter implements CustomerRepositoryPort {

    private final CustomerSpringDataRepository customerRepository;

    public CustomerPersistenceAdapter(CustomerSpringDataRepository customerRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository must not be null");
    }

    @Override
    public Customer save(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        CustomerJpaEntity entity = CustomerJpaEntity.fromDomain(customer);
        CustomerJpaEntity saved = customerRepository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return customerRepository.findById(id).map(CustomerJpaEntity::toDomain);
    }

    @Override
    public Optional<Customer> findByCpf(Cpf cpf) {
        if (cpf == null) {
            return Optional.empty();
        }
        return customerRepository.findByCpf(cpf.value()).map(CustomerJpaEntity::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(Email email) {
        if (email == null) {
            return Optional.empty();
        }
        return customerRepository.findByEmail(email.value()).map(CustomerJpaEntity::toDomain);
    }

    @Override
    public boolean existsByCpf(Cpf cpf) {
        if (cpf == null) {
            return false;
        }
        return customerRepository.existsByCpf(cpf.value());
    }

    @Override
    public boolean existsByEmail(Email email) {
        if (email == null) {
            return false;
        }
        return customerRepository.existsByEmail(email.value());
    }
}
