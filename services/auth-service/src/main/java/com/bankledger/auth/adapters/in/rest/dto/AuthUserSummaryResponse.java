package com.bankledger.auth.adapters.in.rest.dto;

import com.bankledger.auth.domain.model.AuthUser;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sanitized user summary DTO for high-traffic internal queries.
 * Contains ZERO credential hashes, login attempt records, or sensitive token information.
 */
public record AuthUserSummaryResponse(
        UUID customerId,
        String cpf,
        String fullName,
        String email,
        String role,
        String status,
        UUID accountId,
        String branch,
        String accountNumber
) {
    public static AuthUserSummaryResponse fromDomain(AuthUser user, Instant now) {
        Objects.requireNonNull(user, "user must not be null");
        String status = user.isLocked(now) ? "LOCKED" : "ACTIVE";
        return new AuthUserSummaryResponse(
                user.getCustomerId(),
                user.getCpf(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                status,
                user.getAccountId(),
                user.getBranch(),
                user.getAccountNumber()
        );
    }
}
