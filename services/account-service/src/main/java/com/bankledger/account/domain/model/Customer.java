package com.bankledger.account.domain.model;

import com.bankledger.account.domain.exception.CustomerUnderageException;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain aggregate root representing a Banking Customer.
 * Enforces invariants such as legal age (>= 18 years) and brute-force lockout rules.
 * Zero framework dependencies.
 */
public class Customer implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final int LOCKOUT_DURATION_MINUTES = 15;
    public static final int MINIMUM_AGE_YEARS = 18;

    private final UUID id;
    private String fullName;
    private final Cpf cpf;
    private Email email;
    private Phone phone;
    private final LocalDate birthDate;
    private String passwordHash;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Full constructor for reconstitution from persistence.
     */
    public Customer(
            UUID id,
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate,
            String passwordHash,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null").trim();
        if (this.fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        this.cpf = Objects.requireNonNull(cpf, "cpf must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.phone = Objects.requireNonNull(phone, "phone must not be null");
        this.birthDate = Objects.requireNonNull(birthDate, "birthDate must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.failedLoginAttempts = Math.max(0, failedLoginAttempts);
        this.lockedUntil = lockedUntil;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Factory method to create a new customer enforcing age >= 18.
     */
    public static Customer create(
            UUID id,
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate,
            String passwordHash,
            Instant now
    ) {
        Objects.requireNonNull(birthDate, "birthDate must not be null");
        Objects.requireNonNull(now, "now must not be null");

        LocalDate referenceDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (Period.between(birthDate, referenceDate).getYears() < MINIMUM_AGE_YEARS) {
            throw new CustomerUnderageException(
                    String.format("Customer must be at least %d years old to open an account (birthDate: %s)",
                            MINIMUM_AGE_YEARS, birthDate)
            );
        }

        UUID customerId = id != null ? id : UUID.randomUUID();
        return new Customer(
                customerId,
                fullName,
                cpf,
                email,
                phone,
                birthDate,
                passwordHash,
                0,
                null,
                now,
                now
        );
    }

    public static Customer create(
            UUID id,
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate,
            String passwordHash
    ) {
        return create(id, fullName, cpf, email, phone, birthDate, passwordHash, Instant.now());
    }

    public static Customer create(
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate,
            String passwordHash
    ) {
        return create(UUID.randomUUID(), fullName, cpf, email, phone, birthDate, passwordHash, Instant.now());
    }

    /**
     * Returns true if customer is locked at the given instant.
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean isLocked() {
        return isLocked(Instant.now());
    }

    /**
     * Increments failed login attempts and sets lockedUntil to now + 15 minutes
     * if attempts reach or exceed MAX_FAILED_LOGIN_ATTEMPTS (5).
     */
    public void recordFailedLogin(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.lockedUntil != null && !now.isBefore(this.lockedUntil)) {
            // Lockout period has elapsed: reset counter before recording new failure
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            this.lockedUntil = now.plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES);
        }
        this.updatedAt = now;
    }

    public void recordFailedLogin() {
        recordFailedLogin(Instant.now());
    }

    /**
     * Resets failed login attempts to 0 and clears lockedUntil lockout.
     */
    public void resetFailedLogins(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void resetFailedLogins() {
        resetFailedLogins(Instant.now());
    }

    public void updateEmail(Email email, Instant now) {
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void updatePhone(Phone phone, Instant now) {
        this.phone = Objects.requireNonNull(phone, "phone must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void updatePasswordHash(String passwordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    // Standard JavaBeans getters
    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public Cpf getCpf() { return cpf; }
    public Email getEmail() { return email; }
    public Phone getPhone() { return phone; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getPasswordHash() { return passwordHash; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Fluent record-style accessors
    public UUID id() { return id; }
    public String fullName() { return fullName; }
    public Cpf cpf() { return cpf; }
    public Email email() { return email; }
    public Phone phone() { return phone; }
    public LocalDate birthDate() { return birthDate; }
    public String passwordHash() { return passwordHash; }
    public int failedLoginAttempts() { return failedLoginAttempts; }
    public Instant lockedUntil() { return lockedUntil; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
