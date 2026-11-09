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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    private static final Cpf VALID_CPF = new Cpf("52998224725");
    private static final Email VALID_EMAIL = new Email("john.doe@example.com");
    private static final Phone VALID_PHONE = new Phone("+5511987654321");
    private static final String PASSWORD_HASH = "$2a$12$e8YVw8Qe/fD6k1/6iGvK.u";

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
                PASSWORD_HASH,
                now
        );
        assertThat(customer18).isNotNull();
        assertThat(customer18.getFullName()).isEqualTo("John Doe");
        assertThat(customer18.getFailedLoginAttempts()).isZero();
        assertThat(customer18.isLocked(now)).isFalse();

        // 35 years old
        Customer customer35 = Customer.create(
                UUID.randomUUID(),
                "Jane Doe",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                today.minusYears(35),
                PASSWORD_HASH,
                now
        );
        assertThat(customer35).isNotNull();
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
                PASSWORD_HASH,
                now
        )).isInstanceOf(CustomerUnderageException.class);
    }

    @Test
    @DisplayName("Should lock customer for 15 minutes upon 5 failed login attempts")
    void shouldLockCustomerAfterFiveFailedLogins() {
        Instant t0 = Instant.parse("2026-11-09T10:00:00Z");
        Customer customer = Customer.create(
                UUID.randomUUID(),
                "John Doe",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                LocalDate.of(1995, 5, 20),
                PASSWORD_HASH,
                t0
        );

        assertThat(customer.isLocked(t0)).isFalse();

        // Attempts 1 to 4: increments counter, not locked
        for (int i = 1; i <= 4; i++) {
            customer.recordFailedLogin(t0.plus(i, ChronoUnit.MINUTES));
            assertThat(customer.getFailedLoginAttempts()).isEqualTo(i);
            assertThat(customer.isLocked(t0.plus(i, ChronoUnit.MINUTES))).isFalse();
        }

        // 5th attempt at t0 + 5 minutes
        Instant fifthAttempt = t0.plus(5, ChronoUnit.MINUTES);
        customer.recordFailedLogin(fifthAttempt);

        assertThat(customer.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(customer.getLockedUntil()).isEqualTo(fifthAttempt.plus(15, ChronoUnit.MINUTES));

        // During lockout window (e.g. at 5th attempt + 14 minutes) -> isLocked == true
        Instant duringLockout = fifthAttempt.plus(14, ChronoUnit.MINUTES);
        assertThat(customer.isLocked(duringLockout)).isTrue();

        // Exactly at or after lockout window (5th attempt + 15 minutes) -> isLocked == false
        Instant afterLockout = fifthAttempt.plus(15, ChronoUnit.MINUTES);
        assertThat(customer.isLocked(afterLockout)).isFalse();
    }

    @Test
    @DisplayName("Should reset failed login attempts and unlock customer")
    void shouldResetFailedLogins() {
        Instant t0 = Instant.parse("2026-11-09T10:00:00Z");
        Customer customer = Customer.create(
                UUID.randomUUID(),
                "John Doe",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                LocalDate.of(1995, 5, 20),
                PASSWORD_HASH,
                t0
        );

        for (int i = 1; i <= 5; i++) {
            customer.recordFailedLogin(t0);
        }
        assertThat(customer.isLocked(t0)).isTrue();

        customer.resetFailedLogins();

        assertThat(customer.getFailedLoginAttempts()).isZero();
        assertThat(customer.getLockedUntil()).isNull();
        assertThat(customer.isLocked(t0)).isFalse();
    }

    @Test
    @DisplayName("Should reset counter when failing login after lockout period has elapsed")
    void shouldResetCounterWhenFailingAfterLockoutPeriodHasElapsed() {
        Instant t0 = Instant.parse("2026-11-09T10:00:00Z");
        Customer customer = Customer.create(
                UUID.randomUUID(),
                "John Doe",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                LocalDate.of(1995, 5, 20),
                PASSWORD_HASH,
                t0
        );

        for (int i = 1; i <= 5; i++) {
            customer.recordFailedLogin(t0);
        }
        assertThat(customer.isLocked(t0)).isTrue();

        // 16 minutes later (lockout elapsed)
        Instant tAfterLockout = t0.plus(16, ChronoUnit.MINUTES);
        assertThat(customer.isLocked(tAfterLockout)).isFalse();

        // One failure after lockout should become attempt 1, NOT re-lock immediately
        customer.recordFailedLogin(tAfterLockout);
        assertThat(customer.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(customer.isLocked(tAfterLockout)).isFalse();
        assertThat(customer.getLockedUntil()).isNull();
    }
}
