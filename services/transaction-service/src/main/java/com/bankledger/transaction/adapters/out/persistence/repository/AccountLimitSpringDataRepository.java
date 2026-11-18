package com.bankledger.transaction.adapters.out.persistence.repository;

import com.bankledger.transaction.adapters.out.persistence.entity.AccountLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountLimitSpringDataRepository extends JpaRepository<AccountLimitEntity, UUID> {
}
