package com.bankledger.account.application.port.in;

/**
 * Inbound port for opening a new bank account.
 */
public interface CreateAccountUseCase {

    CreatedAccountResult createAccount(CreateAccountCommand command);
}
