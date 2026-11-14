package com.bankledger.query.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalanceEntity, UUID> {
}
