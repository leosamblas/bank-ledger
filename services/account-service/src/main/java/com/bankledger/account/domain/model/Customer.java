package com.bankledger.account.domain.model;

import com.bankledger.account.domain.exception.CustomerUnderageException;
import com.bankledger.account.domain.vo.Address;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain aggregate root representing a Banking Customer.
 * Enforces invariants such as legal age (>= 18 years).
 * Zero framework dependencies.
 */
public class Customer implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int MINIMUM_AGE_YEARS = 18;
    public static final String DELEGATED_PASSWORD_HASH = "[DELEGATED_TO_AUTH_SERVICE]";

    private final UUID id;
    private final String fullName;
    private final Cpf cpf;
    private Email email;
    private Phone phone;
    private final LocalDate birthDate;
    private String passwordHash;
    private Address address;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Full constructor for reconstitution from persistence including Address.
     */
    public Customer(
            UUID id,
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate,
            String passwordHash,
            Address address,
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
        this.passwordHash = (passwordHash != null && !passwordHash.isBlank()) ? passwordHash : DELEGATED_PASSWORD_HASH;
        this.address = address;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public Customer(
            UUID id,
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate,
            String passwordHash,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, fullName, cpf, email, phone, birthDate, passwordHash, null, createdAt, updatedAt);
    }

    public Customer(
            UUID id,
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, fullName, cpf, email, phone, birthDate, DELEGATED_PASSWORD_HASH, null, createdAt, updatedAt);
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
            Address address,
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
                passwordHash != null && !passwordHash.isBlank() ? passwordHash : DELEGATED_PASSWORD_HASH,
                address,
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
            String passwordHash,
            Instant now
    ) {
        return create(id, fullName, cpf, email, phone, birthDate, passwordHash, null, now);
    }

    public static Customer create(
            UUID id,
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate,
            Instant now
    ) {
        return create(id, fullName, cpf, email, phone, birthDate, DELEGATED_PASSWORD_HASH, null, now);
    }

    public static Customer create(
            String fullName,
            Cpf cpf,
            Email email,
            Phone phone,
            LocalDate birthDate
    ) {
        return create(UUID.randomUUID(), fullName, cpf, email, phone, birthDate, DELEGATED_PASSWORD_HASH, null, Instant.now());
    }

    public void updateEmail(Email email, Instant now) {
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void updatePhone(Phone phone, Instant now) {
        this.phone = Objects.requireNonNull(phone, "phone must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void updateAddress(Address address, Instant now) {
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void updateProfile(Phone phone, Email email, Address address, Instant now) {
        this.phone = Objects.requireNonNull(phone, "phone must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
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
    public Address getAddress() { return address; }
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
    public Address address() { return address; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
