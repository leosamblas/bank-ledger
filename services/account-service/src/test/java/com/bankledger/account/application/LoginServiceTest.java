package com.bankledger.account.application;

import com.bankledger.account.application.port.in.LoginCommand;
import com.bankledger.account.application.port.in.LoginResult;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.application.port.out.PasswordEncoderPort;
import com.bankledger.account.application.port.out.TokenServicePort;
import com.bankledger.account.application.service.LoginService;
import com.bankledger.account.domain.exception.AccountNotFoundException;
import com.bankledger.account.domain.exception.CustomerLockedException;
import com.bankledger.account.domain.exception.InvalidCredentialsException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private CustomerRepositoryPort customerRepository;

    @Mock
    private AccountRepositoryPort accountRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @Mock
    private TokenServicePort tokenService;

    private Clock clock;
    private LoginService loginService;

    private static final Instant T0 = Instant.parse("2026-11-09T14:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(T0, ZoneOffset.UTC);
        loginService = new LoginService(
                customerRepository,
                accountRepository,
                passwordEncoder,
                tokenService,
                clock
        );
    }

    private Customer createCustomer(int failedAttempts, Instant lockedUntil) {
        return new Customer(
                UUID.randomUUID(),
                "Carlos Eduardo Silva",
                Cpf.of("52998224725"),
                Email.of("carlos@example.com"),
                Phone.of("+5511999998888"),
                LocalDate.of(1990, 1, 1),
                "$2a$12$encodedPasswordHash",
                failedAttempts,
                lockedUntil,
                T0.minus(1, ChronoUnit.DAYS),
                T0.minus(1, ChronoUnit.DAYS)
        );
    }

    private Account createAccount(UUID customerId) {
        return new Account(
                UUID.randomUUID(),
                customerId,
                "0001",
                AccountNumber.of("0001", "100000", "4"),
                AccountStatus.ACTIVE,
                T0.minus(1, ChronoUnit.DAYS),
                T0.minus(1, ChronoUnit.DAYS),
                null
        );
    }

    @Test
    @DisplayName("Should successfully login and return JWT token when credentials are valid")
    void shouldLoginSuccessfullyWhenCredentialsValid() {
        Customer customer = createCustomer(2, null);
        Account account = createAccount(customer.getId());
        LoginCommand command = new LoginCommand("52998224725", "ValidPassword123!");

        when(customerRepository.findByCpf(Cpf.of("52998224725"))).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("ValidPassword123!", customer.getPasswordHash())).thenReturn(true);
        when(accountRepository.findByCustomerId(customer.getId())).thenReturn(Optional.of(account));
        when(tokenService.generateToken(customer, account)).thenReturn("jwt.token.string");

        LoginResult result = loginService.login(command);

        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("jwt.token.string");
        assertThat(result.expiresIn()).isEqualTo(3600L);
        assertThat(result.customerId()).isEqualTo(customer.getId());
        assertThat(result.accountId()).isEqualTo(account.getId());
        assertThat(result.branch()).isEqualTo("0001");
        assertThat(result.accountNumber()).isEqualTo("100000");
        assertThat(result.customerName()).isEqualTo("Carlos Eduardo Silva");

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(0);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Should fail with InvalidCredentialsException when customer not found to prevent user enumeration")
    void shouldFailWhenCustomerNotFound() {
        LoginCommand command = new LoginCommand("11144477735", "AnyPassword");

        when(customerRepository.findByCpf(Cpf.of("11144477735"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(customerRepository, never()).save(any());
        verify(tokenService, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("Should increment failed attempts on invalid password without locking if below threshold")
    void shouldFailWhenPasswordInvalidAndIncrementFailedAttempts() {
        Customer customer = createCustomer(2, null);
        LoginCommand command = new LoginCommand("52998224725", "WrongPassword");

        when(customerRepository.findByCpf(Cpf.of("52998224725"))).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("WrongPassword", customer.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer savedCustomer = captor.getValue();
        assertThat(savedCustomer.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(savedCustomer.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Should lock customer for 15 minutes on 5th failed login attempt")
    void shouldLockCustomerOnFifthFailedAttemptForFifteenMinutes() {
        Customer customer = createCustomer(4, null);
        LoginCommand command = new LoginCommand("52998224725", "WrongPassword");

        when(customerRepository.findByCpf(Cpf.of("52998224725"))).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("WrongPassword", customer.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(CustomerLockedException.class)
                .hasMessageContaining("Conta bloqueada temporariamente por excesso de tentativas")
                .hasMessageContaining("15 minutos");

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer savedCustomer = captor.getValue();
        assertThat(savedCustomer.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(savedCustomer.getLockedUntil()).isEqualTo(T0.plus(15, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Should immediately reject login when customer is currently locked without checking password")
    void shouldRejectLoginImmediatelyWhenCustomerIsAlreadyLocked() {
        Instant lockedUntil = T0.plus(10, ChronoUnit.MINUTES);
        Customer customer = createCustomer(5, lockedUntil);
        LoginCommand command = new LoginCommand("52998224725", "AnyPassword");

        when(customerRepository.findByCpf(Cpf.of("52998224725"))).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(CustomerLockedException.class)
                .hasMessageContaining("Conta bloqueada temporariamente por excesso de tentativas")
                .hasMessageContaining("10 minutos");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(customerRepository, never()).save(any());
        verify(tokenService, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("Should allow successful login after lockout duration has elapsed")
    void shouldAllowLoginAfterLockoutExpires() {
        // Customer was locked until T0 - 1 minute
        Instant pastLockout = T0.minus(1, ChronoUnit.MINUTES);
        Customer customer = createCustomer(5, pastLockout);
        Account account = createAccount(customer.getId());
        LoginCommand command = new LoginCommand("52998224725", "ValidPassword123!");

        when(customerRepository.findByCpf(Cpf.of("52998224725"))).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("ValidPassword123!", customer.getPasswordHash())).thenReturn(true);
        when(accountRepository.findByCustomerId(customer.getId())).thenReturn(Optional.of(account));
        when(tokenService.generateToken(customer, account)).thenReturn("jwt.token.string");

        LoginResult result = loginService.login(command);

        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("jwt.token.string");

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(0);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException if active account is not found for authenticated customer")
    void shouldFailWhenNoActiveAccountFoundForCustomer() {
        Customer customer = createCustomer(0, null);
        LoginCommand command = new LoginCommand("52998224725", "ValidPassword123!");

        when(customerRepository.findByCpf(Cpf.of("52998224725"))).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("ValidPassword123!", customer.getPasswordHash())).thenReturn(true);
        when(accountRepository.findByCustomerId(customer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Active account not found for customer");

        verify(tokenService, never()).generateToken(any(), any());
    }
}
