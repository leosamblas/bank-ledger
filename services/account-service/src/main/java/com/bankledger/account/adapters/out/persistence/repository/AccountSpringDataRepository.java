package com.bankledger.account.adapters.out.persistence.repository;

import com.bankledger.account.adapters.out.persistence.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AccountEntity}.
 */
public interface AccountSpringDataRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByBranchAndAccountNumber(String branch, String accountNumber);

    Optional<AccountEntity> findByCustomerId(UUID customerId);

    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumberSequence();
}
