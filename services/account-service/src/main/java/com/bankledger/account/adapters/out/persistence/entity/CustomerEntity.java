package com.bankledger.account.adapters.out.persistence.entity;

import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.Address;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity mapping for customer table.
 */
@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "passwordHash")
public class CustomerEntity {

    public static final String DELEGATED_PASSWORD_HASH = "[DELEGATED_TO_AUTH_SERVICE]";

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "postal_code", length = 9)
    private String postalCode;

    @Column(name = "street", length = 150)
    private String street;

    @Column(name = "number", length = 20)
    private String number;

    @Column(name = "complement", length = 100)
    private String complement;

    @Column(name = "neighborhood", length = 100)
    private String neighborhood;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Customer toDomain() {
        Address domainAddress = null;
        if (postalCode != null && !postalCode.isBlank() && street != null && !street.isBlank()) {
            domainAddress = Address.of(postalCode, street, number, complement, neighborhood, city, state);
        }

        return new Customer(
                id,
                fullName,
                Cpf.of(cpf),
                Email.of(email),
                Phone.of(phone),
                birthDate,
                passwordHash != null && !passwordHash.isBlank() ? passwordHash : DELEGATED_PASSWORD_HASH,
                domainAddress,
                createdAt,
                updatedAt
        );
    }

    public static CustomerEntity fromDomain(Customer domain) {
        if (domain == null) {
            return null;
        }
        String effectivePasswordHash = (domain.passwordHash() != null && !domain.passwordHash().isBlank())
                ? domain.passwordHash()
                : DELEGATED_PASSWORD_HASH;

        Address addr = domain.address();

        return CustomerEntity.builder()
                .id(domain.id())
                .fullName(domain.fullName())
                .cpf(domain.cpf().value())
                .email(domain.email().value())
                .phone(domain.phone().value())
                .birthDate(domain.birthDate())
                .passwordHash(effectivePasswordHash)
                .postalCode(addr != null ? addr.postalCode() : null)
                .street(addr != null ? addr.street() : null)
                .number(addr != null ? addr.number() : null)
                .complement(addr != null ? addr.complement() : null)
                .neighborhood(addr != null ? addr.neighborhood() : null)
                .city(addr != null ? addr.city() : null)
                .state(addr != null ? addr.state() : null)
                .failedLoginAttempts(0)
                .lockedUntil(null)
                .createdAt(domain.createdAt())
                .updatedAt(domain.updatedAt())
                .build();
    }
}
