package com.bankledger.account.adapters.in.rest;

import com.bankledger.account.application.port.in.CreateAccountUseCase;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST controller adapter.
 * Accesses only application inbound ports.
 */
@RestController
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;

    public AccountController(CreateAccountUseCase createAccountUseCase) {
        this.createAccountUseCase = createAccountUseCase;
    }
}
