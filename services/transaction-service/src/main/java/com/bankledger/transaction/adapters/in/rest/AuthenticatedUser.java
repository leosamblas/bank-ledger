package com.bankledger.transaction.adapters.in.rest;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedUser(
        UUID customerId,
        UUID accountId,
        String role
) implements Principal {

    public AuthenticatedUser(UUID customerId, UUID accountId) {
        this(customerId, accountId, "ROLE_USER");
    }

    @Override
    public String getName() {
        return customerId != null ? customerId.toString() : "";
    }
}
