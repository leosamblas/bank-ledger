package com.bankledger.query.config;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record AuthenticatedUser(
        UUID customerId,
        UUID accountId,
        String role
) implements Principal, Serializable {

    @Override
    public String getName() {
        return customerId != null ? customerId.toString() : "";
    }
}
