package com.bankledger.account.application.service;

import com.bankledger.account.application.port.in.CreateAccountUseCase;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import org.springframework.stereotype.Service;

/**
 * Application service implementing inbound port and orchestrating domain logic.
 */
@Service
public class CreateAccountService implements CreateAccountUseCase {

    private final AccountRepositoryPort accountRepositoryPort;

    public CreateAccountService(AccountRepositoryPort accountRepositoryPort) {
        this.accountRepositoryPort = accountRepositoryPort;
    }
}

