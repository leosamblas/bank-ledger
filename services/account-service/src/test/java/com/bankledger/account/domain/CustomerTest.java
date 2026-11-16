package com.bankledger.account.domain;

import com.bankledger.account.domain.exception.CustomerUnderageException;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    private static final Cpf VALID_CPF = new Cpf("52998224725");
    private static final Email VALID_EMAIL = new Email("john.doe@example.com");
    private static final Phone VALID_PHONE = new Phone("+5511987654321");

    @Test
    @DisplayName("Should create customer when age is exactly 18 or older")
    void shouldCreateCustomerWhenAdult() {
        LocalDate today = LocalDate.of(2026, 11, 9);
        Instant now = Instant.parse("2026-11-09T10:00:00Z");

        // Exactly 18 years old
        Customer customer18 = Customer.create(
                UUID.randomUUID(),
                "John Doe",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                today.minusYears(18),
                now
        );
        assertThat(customer18).isNotNull();
        assertThat(customer18.getFullName()).isEqualTo("John Doe");
        assertThat(customer18.getPasswordHash()).isEqualTo(Customer.DELEGATED_PASSWORD_HASH);

        // 35 years old
        Customer customer35 = Customer.create(
                UUID.randomUUID(),
                "Jane Doe",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                today.minusYears(35),
                now
        );
        assertThat(customer35).isNotNull();
        assertThat(customer35.getPasswordHash()).isEqualTo(Customer.DELEGATED_PASSWORD_HASH);
    }

    @Test
    @DisplayName("Should throw CustomerUnderageException when customer is under 18")
    void shouldThrowWhenCustomerIsUnder18() {
        LocalDate today = LocalDate.of(2026, 11, 9);
        Instant now = Instant.parse("2026-11-09T10:00:00Z");

        // 17 years and 364 days old
        LocalDate underageBirthDate = today.minusYears(18).plusDays(1);

        assertThatThrownBy(() -> Customer.create(
                UUID.randomUUID(),
                "Minor User",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                underageBirthDate,
                now
        )).isInstanceOf(CustomerUnderageException.class);
    }
}
