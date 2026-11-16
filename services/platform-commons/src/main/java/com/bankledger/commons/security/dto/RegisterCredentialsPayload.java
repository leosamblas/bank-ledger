package com.bankledger.commons.security.dto;

import java.util.UUID;

/**
 * Standard DTO payload for registering credentials from account-service to auth-service.
 */
public record RegisterCredentialsPayload(
        UUID customerId,
        String cpf,
        String password,
        UUID accountId,
        String branch,
        String accountNumber,
        String fullName,
        String email
) {}
