package com.bankledger.account.application;

import com.bankledger.account.application.port.in.AccountMeResult;
import com.bankledger.account.application.port.in.UpdateProfileCommand;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.application.service.UpdateProfileService;
import com.bankledger.account.domain.exception.EmailAlreadyExistsException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Mock
    private AccountRepositoryPort accountRepository;

    @Mock
    private CustomerRepositoryPort customerRepository;

    private UpdateProfileService updateProfileService;

    @BeforeEach
    void setUp() {
        updateProfileService = new UpdateProfileService(accountRepository, customerRepository);
    }

    private Customer createCustomer(UUID id, String name) {
        return new Customer(
                id,
                name,
                Cpf.of("52998224725"),
                Email.of("carlos@example.com"),
                Phone.of("+5511988887777"),
                LocalDate.of(1990, 5, 15),
                NOW,
                NOW
        );
    }

    private Account createAccount(UUID accountId, UUID customerId, String branch, String number, String digit) {
        return new Account(
                accountId,
                customerId,
                branch,
                AccountNumber.of(branch, number, digit),
                AccountStatus.ACTIVE,
                NOW,
                NOW,
                null
        );
    }

    @Test
    @DisplayName("Should update customer profile (email, phone, address) and keep cpf, birthDate, fullName immutable")
    void shouldUpdateProfileSuccessfully() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.findByEmail(Email.of("new.email@example.com"))).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileCommand command = new UpdateProfileCommand(
                accountId,
                "+5511977776666",
                "new.email@example.com",
                "01310-100",
                "Avenida Paulista",
                "1500",
                "Conjunto 50",
                "Bela Vista",
                "São Paulo",
                "SP"
        );

        AccountMeResult result = updateProfileService.updateProfile(command);

        assertThat(result).isNotNull();
        assertThat(result.holderName()).isEqualTo("Carlos Eduardo Silva");
        assertThat(result.holderCpf()).isEqualTo("52998224725");
        assertThat(result.holderEmail()).isEqualTo("new.email@example.com");
        assertThat(result.holderPhone()).isEqualTo("+5511977776666");
        assertThat(result.address()).isNotNull();
        assertThat(result.address().street()).isEqualTo("Avenida Paulista");
        assertThat(result.address().number()).isEqualTo("1500");
    }

    @Test
    @DisplayName("Should throw EmailAlreadyExistsException when updating profile to an email owned by another customer")
    void shouldThrowWhenEmailBelongsToAnotherCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva");
        Customer otherCustomer = createCustomer(otherCustomerId, "Other Person");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.findByEmail(Email.of("existing@example.com"))).thenReturn(Optional.of(otherCustomer));

        UpdateProfileCommand command = new UpdateProfileCommand(
                accountId,
                "+5511977776666",
                "existing@example.com",
                "01310-100",
                "Avenida Paulista",
                "1500",
                null,
                "Bela Vista",
                "São Paulo",
                "SP"
        );

        assertThatThrownBy(() -> updateProfileService.updateProfile(command))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email already in use");
    }
}
