package com.bankledger.transaction.adapters.in.rest;

import com.bankledger.commons.security.CommonAuthenticatedUser;

import java.security.Principal;
import java.util.UUID;

/**
 * Principal representing authenticated user in transaction-service, integrating with {@link CommonAuthenticatedUser}.
 */
public record AuthenticatedUser(
        UUID customerId,
        UUID accountId,
        String role
) implements Principal {

    public AuthenticatedUser(UUID customerId, UUID accountId) {
        this(customerId, accountId, "ROLE_USER");
    }

    public static AuthenticatedUser fromCommon(CommonAuthenticatedUser user) {
        if (user == null) {
            return null;
        }
        return new AuthenticatedUser(user.customerId(), user.accountId(), user.role());
    }

    public CommonAuthenticatedUser toCommon() {
        return new CommonAuthenticatedUser(customerId, accountId, null, null, role);
    }

    @Override
    public String getName() {
        return customerId != null ? customerId.toString() : "";
    }
}
