package com.bankledger.auth.application.port.in;

public interface LoginUseCase {
    LoginResult login(LoginCommand command);
}
