package com.bankledger.auth.adapters.out.persistence;

import com.bankledger.auth.domain.model.AuthUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_credentials")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthUserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 30)
    private String role;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "branch", length = 10)
    private String branch;

    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "active_token", columnDefinition = "TEXT")
    private String activeToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "refresh_token", length = 128)
    private String refreshToken;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AuthUser toDomain() {
        return new AuthUser(
                this.id,
                this.cpf,
                this.passwordHash,
                this.role,
                this.customerId,
                this.accountId,
                this.branch,
                this.accountNumber,
                this.failedLoginAttempts,
                this.lockedUntil,
                this.fullName,
                this.email,
                this.activeToken,
                this.tokenExpiresAt,
                this.refreshToken,
                this.refreshTokenExpiresAt,
                this.createdAt,
                this.updatedAt
        );
    }

    public static AuthUserEntity fromDomain(AuthUser user) {
        return AuthUserEntity.builder()
                .id(user.getId())
                .cpf(user.getCpf())
                .passwordHash(user.getPasswordHash())
                .role(user.getRole())
                .customerId(user.getCustomerId())
                .accountId(user.getAccountId())
                .branch(user.getBranch())
                .accountNumber(user.getAccountNumber())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lockedUntil(user.getLockedUntil())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .activeToken(user.getActiveToken())
                .tokenExpiresAt(user.getTokenExpiresAt())
                .refreshToken(user.getRefreshToken())
                .refreshTokenExpiresAt(user.getRefreshTokenExpiresAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
