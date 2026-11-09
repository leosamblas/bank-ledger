package com.bankledger.account.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for AccountJpaEntity.
 */
@Repository
public interface AccountSpringDataRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByBranchAndAccountNumber(String branch, String accountNumber);

    Optional<AccountJpaEntity> findByCustomerId(UUID customerId);

    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumberSequence();
}
