package com.bankledger.auth.application.port.in;

import com.bankledger.auth.domain.model.AuthUser;

public interface RegisterCredentialsUseCase {
    AuthUser registerCredentials(RegisterCredentialsCommand command);
}
