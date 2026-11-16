package com.bankledger.auth.application.port.in;

public interface RefreshTokenUseCase {
    LoginResult refreshToken(RefreshTokenCommand command);
}
