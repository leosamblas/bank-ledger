package com.bankledger.auth.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public class AuthUser {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCKOUT_MINUTES = 15;

    private final UUID id;
    private final String cpf;
    private String passwordHash;
    private String role;
    private final UUID customerId;
    private UUID accountId;
    private String branch;
    private String accountNumber;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private String fullName;
    private String email;
    private String activeToken;
    private Instant tokenExpiresAt;
    private String refreshToken;
    private Instant refreshTokenExpiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public AuthUser(
            UUID id,
            String cpf,
            String passwordHash,
            String role,
            UUID customerId,
            UUID accountId,
            String branch,
            String accountNumber,
            int failedLoginAttempts,
            Instant lockedUntil,
            String fullName,
            String email,
            String activeToken,
            Instant tokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.cpf = Objects.requireNonNull(cpf, "cpf must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.role = (role != null && !role.isBlank()) ? role : "ROLE_USER";
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.accountId = accountId;
        this.branch = branch;
        this.accountNumber = accountNumber;
        this.failedLoginAttempts = Math.max(0, failedLoginAttempts);
        this.lockedUntil = lockedUntil;
        this.fullName = fullName;
        this.email = email;
        this.activeToken = activeToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.refreshToken = refreshToken;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public AuthUser(
            UUID id,
            String cpf,
            String passwordHash,
            String role,
            UUID customerId,
            UUID accountId,
            String branch,
            String accountNumber,
            int failedLoginAttempts,
            Instant lockedUntil,
            String fullName,
            String email,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, cpf, passwordHash, role, customerId, accountId, branch, accountNumber,
                failedLoginAttempts, lockedUntil, fullName, email, null, null, null, null, createdAt, updatedAt);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean hasActiveToken(Instant now) {
        return activeToken != null && tokenExpiresAt != null && tokenExpiresAt.isAfter(now);
    }

    public void updateTokens(
            String activeToken,
            Instant tokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            Instant now
    ) {
        this.activeToken = activeToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.refreshToken = refreshToken;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void recordFailedLogin(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES);
        }
        updatedAt = now;
    }

    public void resetFailedLogins(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCpf() { return cpf; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public UUID getCustomerId() { return customerId; }
    public UUID getAccountId() { return accountId; }
    public String getBranch() { return branch; }
    public String getAccountNumber() { return accountNumber; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getActiveToken() { return activeToken; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
