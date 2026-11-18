package com.bankledger.account.application;

import com.bankledger.account.application.port.in.AccountLookupResult;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.application.service.AccountQueryService;
import com.bankledger.account.domain.exception.AccountNotFoundException;
import com.bankledger.account.domain.exception.CustomerNotFoundException;
import com.bankledger.account.domain.exception.InvalidCpfException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.AccountNumber;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLookupByCpfTest {

    @Mock
    private AccountRepositoryPort accountRepository;

    @Mock
    private CustomerRepositoryPort customerRepository;

    private AccountQueryService accountQueryService;

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final String CPF_RAW = "52998224725";
    private static final String CPF_FORMATTED = "529.982.247-25";

    @BeforeEach
    void setUp() {
        accountQueryService = new AccountQueryService(accountRepository, customerRepository);
    }

    private Customer createCustomer(UUID id, String fullName, String cpfDigits) {
        return new Customer(
                id,
                fullName,
                Cpf.of(cpfDigits),
                Email.of("carlos@example.com"),
                Phone.of("+5511999998888"),
                LocalDate.of(1990, 1, 1),
                NOW,
                NOW
        );
    }

    private Account createAccount(UUID id, UUID customerId, String branch, String number, String dv) {
        return new Account(
                id,
                customerId,
                branch,
                AccountNumber.of(branch, number, dv),
                AccountStatus.ACTIVE,
                NOW,
                NOW,
                null
        );
    }

    @Test
    @DisplayName("Should lookup account by CPF with raw digits and return unmasked holder data")
    void shouldLookupAccountByCpfRawDigits() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva", CPF_RAW);
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");

        when(customerRepository.findByCpf(Cpf.of(CPF_RAW))).thenReturn(Optional.of(customer));
        when(accountRepository.findByCustomerId(customerId)).thenReturn(Optional.of(account));

        AccountLookupResult result = accountQueryService.lookup("CPF", CPF_RAW, null, null);

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.branch()).isEqualTo("0001");
        assertThat(result.accountNumber()).isEqualTo("100000");
        assertThat(result.checkDigit()).isEqualTo("4");
        assertThat(result.holderName()).isEqualTo("Carlos Eduardo Silva");
        assertThat(result.cpf()).isEqualTo(CPF_FORMATTED);
        assertThat(result.bankCode()).isEqualTo("001");
        assertThat(result.bankName()).isEqualTo("Nexus Bank S.A.");
    }

    @Test
    @DisplayName("Should lookup account by CPF with formatted string and return unmasked holder data")
    void shouldLookupAccountByCpfFormattedString() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva", CPF_RAW);
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");

        when(customerRepository.findByCpf(Cpf.of(CPF_RAW))).thenReturn(Optional.of(customer));
        when(accountRepository.findByCustomerId(customerId)).thenReturn(Optional.of(account));

        AccountLookupResult result = accountQueryService.lookup("CPF", CPF_FORMATTED, null, null);

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.holderName()).isEqualTo("Carlos Eduardo Silva");
        assertThat(result.cpf()).isEqualTo(CPF_FORMATTED);
    }

    @Test
    @DisplayName("Should lookup account by ACCOUNT searchType and return unmasked holder data")
    void shouldLookupAccountByAgencyAndAccountNumber() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva", CPF_RAW);
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");

        when(accountRepository.findByBranchAndNumber("0001", "100000")).thenReturn(Optional.of(account));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        AccountLookupResult result = accountQueryService.lookup("ACCOUNT", null, "0001", "100000-4");

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.branch()).isEqualTo("0001");
        assertThat(result.accountNumber()).isEqualTo("100000");
        assertThat(result.checkDigit()).isEqualTo("4");
        assertThat(result.holderName()).isEqualTo("Carlos Eduardo Silva");
        assertThat(result.cpf()).isEqualTo(CPF_FORMATTED);
        assertThat(result.bankCode()).isEqualTo("001");
        assertThat(result.bankName()).isEqualTo("Nexus Bank S.A.");
    }

    @Test
    @DisplayName("Should throw CustomerNotFoundException when CPF is not found")
    void shouldThrowCustomerNotFoundWhenCpfNotFound() {
        when(customerRepository.findByCpf(Cpf.of(CPF_RAW))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountQueryService.lookup("CPF", CPF_RAW, null, null))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining(CPF_RAW);
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when customer exists but account is missing")
    void shouldThrowAccountNotFoundWhenCustomerHasNoAccount() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva", CPF_RAW);

        when(customerRepository.findByCpf(Cpf.of(CPF_RAW))).thenReturn(Optional.of(customer));
        when(accountRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountQueryService.lookup("CPF", CPF_RAW, null, null))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw InvalidCpfException when invalid CPF checksum is provided")
    void shouldThrowInvalidCpfWhenChecksumInvalid() {
        assertThatThrownBy(() -> accountQueryService.lookup("CPF", "11111111111", null, null))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when neither CPF nor ACCOUNT parameters are valid")
    void shouldThrowIllegalArgumentWhenMissingParams() {
        assertThatThrownBy(() -> accountQueryService.lookup("UNKNOWN", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
