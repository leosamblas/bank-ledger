package com.bankledger.account.application.port.out;

import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;

/**
 * Outbound port for generating and parsing security tokens (JWT).
 * Implemented by security adapters.
 */
public interface TokenServicePort {

    String generateToken(Customer customer, Account account);

    TokenClaims parseToken(String token);
}
