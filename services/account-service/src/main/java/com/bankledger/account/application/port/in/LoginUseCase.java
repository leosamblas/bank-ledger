package com.bankledger.account.application.port.in;

/**
 * Inbound port for customer login authentication.
 */
public interface LoginUseCase {

    LoginResult login(LoginCommand command);
}
