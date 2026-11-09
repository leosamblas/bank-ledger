package com.bankledger.account.adapters.in.rest;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedUser(
        UUID customerId,
        UUID accountId,
        String branch,
        String accountNumber,
        String role
) implements Principal {

    @Override
    public String getName() {
        return customerId != null ? customerId.toString() : "";
    }
}

