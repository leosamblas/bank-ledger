package com.bankledger.account.application;

import com.bankledger.account.application.port.in.AccountLookupResult;
import com.bankledger.account.application.port.in.AccountMeResult;
import com.bankledger.account.application.port.in.LookupAccountQuery;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.application.service.AccountQueryService;
import com.bankledger.account.domain.exception.AccountNotFoundException;
import com.bankledger.account.domain.exception.CustomerNotFoundException;
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
class AccountQueryServiceTest {

    @Mock
    private AccountRepositoryPort accountRepository;

    @Mock
    private CustomerRepositoryPort customerRepository;

    private AccountQueryService accountQueryService;

    private static final Instant NOW = Instant.parse("2026-11-09T15:00:00Z");

    @BeforeEach
    void setUp() {
        accountQueryService = new AccountQueryService(accountRepository, customerRepository);
    }

    private Customer createCustomer(UUID id, String fullName) {
        return new Customer(
                id,
                fullName,
                Cpf.of("52998224725"),
                Email.of("carlos@example.com"),
                Phone.of("+5511999998888"),
                LocalDate.of(1990, 1, 1),
                "$2a$12$passwordHash",
                0,
                null,
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
    @DisplayName("Should lookup account by branch and number and mask holder name correctly")
    void shouldLookupAccountAndMaskHolderName() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva");

        when(accountRepository.findByBranchAndNumber("0001", "100000")).thenReturn(Optional.of(account));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        AccountLookupResult result = accountQueryService.lookup("0001", "100000");

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.branch()).isEqualTo("0001");
        assertThat(result.accountNumber()).isEqualTo("100000");
        assertThat(result.checkDigit()).isEqualTo("4");
        assertThat(result.holderNameMasked()).isEqualTo("C*** E*** S***");
    }

    @Test
    @DisplayName("Should lookup account using LookupAccountQuery with formatted number containing hyphen")
    void shouldLookupAccountWithFormattedNumberWithHyphen() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");
        Customer customer = createCustomer(customerId, "Maria Oliveira");

        when(accountRepository.findByBranchAndNumber("0001", "100000")).thenReturn(Optional.of(account));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        LookupAccountQuery query = new LookupAccountQuery("0001", "100000-4");
        AccountLookupResult result = accountQueryService.lookup(query);

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.holderNameMasked()).isEqualTo("M*** O***");
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account is not found during lookup")
    void shouldThrowAccountNotFoundExceptionWhenAccountNotFound() {
        when(accountRepository.findByBranchAndNumber("0001", "999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountQueryService.lookup("0001", "999999"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("0001/999999");
    }

    @Test
    @DisplayName("Should throw CustomerNotFoundException when account exists but customer is missing during lookup")
    void shouldThrowCustomerNotFoundExceptionWhenCustomerMissing() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");

        when(accountRepository.findByBranchAndNumber("0001", "100000")).thenReturn(Optional.of(account));
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountQueryService.lookup("0001", "100000"))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }

    @Test
    @DisplayName("Should retrieve account me details by account ID")
    void shouldGetAccountMeByAccountId() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        AccountMeResult result = accountQueryService.getAccountMe(accountId);

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.branch()).isEqualTo("0001");
        assertThat(result.accountNumber()).isEqualTo("100000");
        assertThat(result.checkDigit()).isEqualTo("4");
        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.holderName()).isEqualTo("Carlos Eduardo Silva");
        assertThat(result.holderCpf()).isEqualTo("52998224725");
        assertThat(result.holderEmail()).isEqualTo("carlos@example.com");
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account ID is not found for getAccountMe")
    void shouldThrowAccountNotFoundExceptionWhenGetAccountMeNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(accountRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountQueryService.getAccountMe(nonExistentId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    @DisplayName("Should retrieve account me details by customer ID")
    void shouldGetAccountMeByCustomerId() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = createAccount(accountId, customerId, "0001", "100000", "4");
        Customer customer = createCustomer(customerId, "Carlos Eduardo Silva");

        when(accountRepository.findByCustomerId(customerId)).thenReturn(Optional.of(account));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        AccountMeResult result = accountQueryService.getAccountByCustomerId(customerId);

        assertThat(result).isNotNull();
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.holderName()).isEqualTo("Carlos Eduardo Silva");
    }
}
