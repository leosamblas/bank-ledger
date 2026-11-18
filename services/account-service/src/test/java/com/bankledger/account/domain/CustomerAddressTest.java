package com.bankledger.account.domain;

import com.bankledger.account.adapters.out.persistence.entity.AccountEntity;
import com.bankledger.account.adapters.out.persistence.entity.CustomerEntity;
import com.bankledger.account.domain.exception.InvalidAddressException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAddressTest {

    private static final Cpf VALID_CPF = new Cpf("52998224725");
    private static final Email VALID_EMAIL = new Email("carlos@example.com");
    private static final Phone VALID_PHONE = new Phone("+5511987654321");
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 1);
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    @DisplayName("Should create valid Address VO with normalized fields")
    void shouldCreateValidAddress() {
        Address address = Address.of(
                "01310-100",
                "Avenida Paulista",
                "1000",
                "Apto 101",
                "Bela Vista",
                "São Paulo",
                "sp"
        );

        assertThat(address.postalCode()).isEqualTo("01310-100");
        assertThat(address.street()).isEqualTo("Avenida Paulista");
        assertThat(address.number()).isEqualTo("1000");
        assertThat(address.complement()).isEqualTo("Apto 101");
        assertThat(address.neighborhood()).isEqualTo("Bela Vista");
        assertThat(address.city()).isEqualTo("São Paulo");
        assertThat(address.state()).isEqualTo("SP");
    }

    @Test
    @DisplayName("Should format 8-digit postal code to standard format")
    void shouldFormat8DigitPostalCode() {
        Address address = Address.of(
                "01310100",
                "Avenida Paulista",
                "1000",
                null,
                "Bela Vista",
                "São Paulo",
                "SP"
        );

        assertThat(address.postalCode()).isEqualTo("01310-100");
        assertThat(address.complement()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "123", "01310-1000", "ABCDE-FGH"})
    @DisplayName("Should throw InvalidAddressException for invalid postal code")
    void shouldThrowForInvalidPostalCode(String postalCode) {
        assertThatThrownBy(() -> Address.of(
                postalCode,
                "Rua das Flores",
                "123",
                null,
                "Centro",
                "Curitiba",
                "PR"
        )).isInstanceOf(InvalidAddressException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should throw InvalidAddressException for blank street, number, neighborhood, or city")
    void shouldThrowForBlankRequiredAddressFields(String blankValue) {
        assertThatThrownBy(() -> Address.of("01310-100", blankValue, "100", null, "Centro", "SP", "SP"))
                .isInstanceOf(InvalidAddressException.class);

        assertThatThrownBy(() -> Address.of("01310-100", "Rua A", blankValue, null, "Centro", "SP", "SP"))
                .isInstanceOf(InvalidAddressException.class);

        assertThatThrownBy(() -> Address.of("01310-100", "Rua A", "100", null, blankValue, "SP", "SP"))
                .isInstanceOf(InvalidAddressException.class);

        assertThatThrownBy(() -> Address.of("01310-100", "Rua A", "100", null, "Centro", blankValue, "SP"))
                .isInstanceOf(InvalidAddressException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"XYZ", "12", "S", "Sao Paulo"})
    @DisplayName("Should throw InvalidAddressException for invalid state (UF)")
    void shouldThrowForInvalidState(String invalidState) {
        assertThatThrownBy(() -> Address.of(
                "01310-100",
                "Avenida Paulista",
                "1000",
                null,
                "Bela Vista",
                "São Paulo",
                invalidState
        )).isInstanceOf(InvalidAddressException.class);
    }

    @Test
    @DisplayName("Customer aggregate should support Address and address updates")
    void shouldSupportCustomerAddressAndUpdates() {
        Address initialAddress = Address.of(
                "01310-100",
                "Avenida Paulista",
                "1000",
                null,
                "Bela Vista",
                "São Paulo",
                "SP"
        );

        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(
                customerId,
                "Carlos Eduardo Silva",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                BIRTH_DATE,
                Customer.DELEGATED_PASSWORD_HASH,
                initialAddress,
                NOW,
                NOW
        );

        assertThat(customer.address()).isEqualTo(initialAddress);
        assertThat(customer.getAddress()).isEqualTo(initialAddress);

        Address newAddress = Address.of(
                "20040-002",
                "Avenida Rio Branco",
                "500",
                "Sala 204",
                "Centro",
                "Rio de Janeiro",
                "RJ"
        );
        Instant updateTime = NOW.plusSeconds(3600);
        customer.updateAddress(newAddress, updateTime);

        assertThat(customer.address()).isEqualTo(newAddress);
        assertThat(customer.updatedAt()).isEqualTo(updateTime);
    }

    @Test
    @DisplayName("Account aggregate should support default and custom dailyTransferLimitCents")
    void shouldSupportDailyTransferLimitCents() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        AccountNumber accountNumber = AccountNumber.of("0001", "100000", "4");

        // Default limit: 5_000_000L (R$ 50.000,00)
        Account account = Account.create(accountId, customerId, "0001", accountNumber, NOW);
        assertThat(account.dailyTransferLimitCents()).isEqualTo(5_000_000L);
        assertThat(account.getDailyTransferLimitCents()).isEqualTo(5_000_000L);

        // Custom limit constructor
        Account customLimitAccount = new Account(
                accountId,
                customerId,
                "0001",
                accountNumber,
                AccountStatus.ACTIVE,
                15_000_000L,
                NOW,
                NOW,
                null
        );
        assertThat(customLimitAccount.dailyTransferLimitCents()).isEqualTo(15_000_000L);

        // Update limit
        customLimitAccount.updateDailyTransferLimit(25_000_000L, NOW.plusSeconds(60));
        assertThat(customLimitAccount.dailyTransferLimitCents()).isEqualTo(25_000_000L);
    }

    @Test
    @DisplayName("CustomerEntity and AccountEntity persistence mapping should correctly map Address and dailyTransferLimitCents")
    void shouldMapEntitiesToAndFromDomain() {
        Address address = Address.of(
                "01310-100",
                "Avenida Paulista",
                "1000",
                "Apto 101",
                "Bela Vista",
                "São Paulo",
                "SP"
        );
        Customer customer = new Customer(
                UUID.randomUUID(),
                "Carlos Eduardo Silva",
                VALID_CPF,
                VALID_EMAIL,
                VALID_PHONE,
                BIRTH_DATE,
                Customer.DELEGATED_PASSWORD_HASH,
                address,
                NOW,
                NOW
        );

        CustomerEntity customerEntity = CustomerEntity.fromDomain(customer);
        assertThat(customerEntity.getPostalCode()).isEqualTo("01310-100");
        assertThat(customerEntity.getStreet()).isEqualTo("Avenida Paulista");
        assertThat(customerEntity.getNumber()).isEqualTo("1000");
        assertThat(customerEntity.getComplement()).isEqualTo("Apto 101");
        assertThat(customerEntity.getNeighborhood()).isEqualTo("Bela Vista");
        assertThat(customerEntity.getCity()).isEqualTo("São Paulo");
        assertThat(customerEntity.getState()).isEqualTo("SP");

        Customer reconstitutedCustomer = customerEntity.toDomain();
        assertThat(reconstitutedCustomer.address()).isNotNull();
        assertThat(reconstitutedCustomer.address().postalCode()).isEqualTo("01310-100");
        assertThat(reconstitutedCustomer.address().street()).isEqualTo("Avenida Paulista");
        assertThat(reconstitutedCustomer.address().state()).isEqualTo("SP");

        AccountNumber accountNumber = AccountNumber.of("0001", "100000", "4");
        Account account = new Account(
                UUID.randomUUID(),
                customer.id(),
                "0001",
                accountNumber,
                AccountStatus.ACTIVE,
                7_500_000L,
                NOW,
                NOW,
                null
        );

        AccountEntity accountEntity = AccountEntity.fromDomain(account);
        assertThat(accountEntity.getDailyTransferLimitCents()).isEqualTo(7_500_000L);

        Account reconstitutedAccount = accountEntity.toDomain();
        assertThat(reconstitutedAccount.dailyTransferLimitCents()).isEqualTo(7_500_000L);
    }
}
