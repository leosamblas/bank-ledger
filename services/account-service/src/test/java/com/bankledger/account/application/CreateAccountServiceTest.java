package com.bankledger.account.application;

import com.bankledger.account.application.port.in.CreateAccountCommand;
import com.bankledger.account.application.port.in.CreatedAccountResult;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.application.port.out.OutboxPort;
import com.bankledger.account.application.service.CreateAccountService;
import com.bankledger.account.domain.event.AccountOpenedEvent;
import com.bankledger.account.domain.exception.CpfAlreadyExistsException;
import com.bankledger.account.domain.exception.CustomerUnderageException;
import com.bankledger.account.domain.exception.EmailAlreadyExistsException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.commons.outbox.OutboxEvent;
import com.bankledger.commons.security.dto.RegisterCredentialsPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAccountServiceTest {

    @Mock
    private CustomerRepositoryPort customerRepository;

    @Mock
    private AccountRepositoryPort accountRepository;

    @Mock
    private OutboxPort outboxPort;

    private ObjectMapper objectMapper;
    private Clock fixedClock;
    private CreateAccountService createAccountService;

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        createAccountService = new CreateAccountService(
                customerRepository,
                accountRepository,
                outboxPort,
                objectMapper,
                fixedClock
        );
    }

    @Test
    @DisplayName("Should successfully create account, customer, and atomic outbox events for account opened and credentials registration")
    void shouldCreateAccountSuccessfullyWithAtomicOutboxEvent() throws Exception {
        CreateAccountCommand command = new CreateAccountCommand(
                "Carlos Eduardo Silva",
                "52998224725",
                LocalDate.of(1990, 1, 1),
                "carlos.silva@example.com",
                "+5511999998888",
                "StrongPassword123!"
        );

        when(customerRepository.existsByCpf(Cpf.of("52998224725"))).thenReturn(false);
        when(customerRepository.existsByEmail(Email.of("carlos.silva@example.com"))).thenReturn(false);
        when(accountRepository.nextAccountNumber()).thenReturn(100000L);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatedAccountResult result = createAccountService.createAccount(command);

        assertThat(result).isNotNull();
        assertThat(result.branch()).isEqualTo("0001");
        assertThat(result.accountNumber()).isEqualTo("100000");
        assertThat(result.checkDigit()).isEqualTo("4");
        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.createdAt()).isEqualTo(NOW);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();
        assertThat(savedCustomer.getFullName()).isEqualTo("Carlos Eduardo Silva");
        assertThat(savedCustomer.getCpf().value()).isEqualTo("52998224725");
        assertThat(savedCustomer.getEmail().value()).isEqualTo("carlos.silva@example.com");
        assertThat(savedCustomer.getPasswordHash()).isEqualTo(Customer.DELEGATED_PASSWORD_HASH);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getCustomerId()).isEqualTo(savedCustomer.getId());
        assertThat(savedAccount.getBranch()).isEqualTo("0001");
        assertThat(savedAccount.getAccountNumber().number()).isEqualTo("100000");
        assertThat(savedAccount.getAccountNumber().checkDigit()).isEqualTo("4");
        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxPort, times(2)).save(outboxCaptor.capture());
        List<OutboxEvent> savedEvents = outboxCaptor.getAllValues();

        // 1. AccountOpened outbox event
        OutboxEvent accountOpenedEvent = savedEvents.stream()
                .filter(e -> "Account".equals(e.getAggregateType()))
                .findFirst()
                .orElseThrow();
        assertThat(accountOpenedEvent.getAggregateId()).isEqualTo(savedAccount.getId());
        assertThat(accountOpenedEvent.getEventType()).isEqualTo(AccountOpenedEvent.EVENT_TYPE);
        assertThat(accountOpenedEvent.getTopic()).isEqualTo(AccountOpenedEvent.TOPIC);
        assertThat(accountOpenedEvent.getMessageKey()).isEqualTo(savedAccount.getId().toString());

        AccountOpenedEvent payloadEvent = objectMapper.readValue(accountOpenedEvent.getPayload(), AccountOpenedEvent.class);
        assertThat(payloadEvent.accountId()).isEqualTo(savedAccount.getId());
        assertThat(payloadEvent.customerId()).isEqualTo(savedCustomer.getId());
        assertThat(payloadEvent.customerCpf()).isEqualTo("52998224725");
        assertThat(payloadEvent.customerName()).isEqualTo("Carlos Eduardo Silva");
        assertThat(payloadEvent.branch()).isEqualTo("0001");
        assertThat(payloadEvent.accountNumber()).isEqualTo("100000");
        assertThat(payloadEvent.checkDigit()).isEqualTo("4");
        assertThat(payloadEvent.openedAt()).isEqualTo(NOW);

        // 2. AuthCredentials registration outbox event
        OutboxEvent credentialsEvent = savedEvents.stream()
                .filter(e -> "AuthCredentials".equals(e.getAggregateType()))
                .findFirst()
                .orElseThrow();
        assertThat(credentialsEvent.getAggregateId()).isEqualTo(savedCustomer.getId());
        assertThat(credentialsEvent.getEventType()).isEqualTo("RegisterCredentials");
        assertThat(credentialsEvent.getTopic()).isEqualTo(KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1);
        assertThat(credentialsEvent.getMessageKey()).isEqualTo(savedCustomer.getId().toString());

        RegisterCredentialsPayload credentialsPayload = objectMapper.readValue(
                credentialsEvent.getPayload(),
                RegisterCredentialsPayload.class
        );
        assertThat(credentialsPayload.customerId()).isEqualTo(savedCustomer.getId());
        assertThat(credentialsPayload.cpf()).isEqualTo("52998224725");
        assertThat(credentialsPayload.password()).isEqualTo("StrongPassword123!");
        assertThat(credentialsPayload.accountId()).isEqualTo(savedAccount.getId());
        assertThat(credentialsPayload.branch()).isEqualTo("0001");
        assertThat(credentialsPayload.accountNumber()).isEqualTo("100000");
        assertThat(credentialsPayload.fullName()).isEqualTo("Carlos Eduardo Silva");
        assertThat(credentialsPayload.email()).isEqualTo("carlos.silva@example.com");
    }

    @Test
    @DisplayName("Should register credentials asynchronously via OutboxEvent with correct payload and topic")
    void shouldRegisterCredentialsAsynchronouslyViaOutbox() throws Exception {
        CreateAccountCommand command = new CreateAccountCommand(
                "Maria Souza",
                "11144477735",
                LocalDate.of(1995, 6, 20),
                "maria.souza@example.com",
                "+5511977776666",
                "SecretPassword@2026"
        );

        when(customerRepository.existsByCpf(Cpf.of("11144477735"))).thenReturn(false);
        when(customerRepository.existsByEmail(Email.of("maria.souza@example.com"))).thenReturn(false);
        when(accountRepository.nextAccountNumber()).thenReturn(200000L);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatedAccountResult result = createAccountService.createAccount(command);

        assertThat(result).isNotNull();

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxPort, times(2)).save(outboxCaptor.capture());

        OutboxEvent credentialsEvent = outboxCaptor.getAllValues().stream()
                .filter(e -> "AuthCredentials".equals(e.getAggregateType()))
                .findFirst()
                .orElseThrow();

        assertThat(credentialsEvent.getAggregateType()).isEqualTo("AuthCredentials");
        assertThat(credentialsEvent.getAggregateId()).isEqualTo(result.customerId());
        assertThat(credentialsEvent.getEventType()).isEqualTo("RegisterCredentials");
        assertThat(credentialsEvent.getTopic()).isEqualTo(KafkaTopics.AUTH_CREDENTIALS_REGISTRATION_V1);
        assertThat(credentialsEvent.getMessageKey()).isEqualTo(result.customerId().toString());

        RegisterCredentialsPayload payload = objectMapper.readValue(
                credentialsEvent.getPayload(),
                RegisterCredentialsPayload.class
        );
        assertThat(payload.customerId()).isEqualTo(result.customerId());
        assertThat(payload.cpf()).isEqualTo("11144477735");
        assertThat(payload.password()).isEqualTo("SecretPassword@2026");
        assertThat(payload.accountId()).isEqualTo(result.accountId());
        assertThat(payload.branch()).isEqualTo("0001");
        assertThat(payload.accountNumber()).isEqualTo("200000");
        assertThat(payload.fullName()).isEqualTo("Maria Souza");
        assertThat(payload.email()).isEqualTo("maria.souza@example.com");
    }

    @Test
    @DisplayName("Should reject account creation when CPF already exists")
    void shouldRejectWhenCpfAlreadyExists() {
        CreateAccountCommand command = new CreateAccountCommand(
                "Carlos Silva",
                "52998224725",
                LocalDate.of(1990, 1, 1),
                "carlos.silva@example.com",
                "+5511999998888",
                "StrongPassword123!"
        );

        when(customerRepository.existsByCpf(Cpf.of("52998224725"))).thenReturn(true);

        assertThatThrownBy(() -> createAccountService.createAccount(command))
                .isInstanceOf(CpfAlreadyExistsException.class)
                .hasMessageContaining("52998224725");

        verify(customerRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        verify(outboxPort, never()).save(any());
    }

    @Test
    @DisplayName("Should reject account creation when Email already exists")
    void shouldRejectWhenEmailAlreadyExists() {
        CreateAccountCommand command = new CreateAccountCommand(
                "Carlos Silva",
                "52998224725",
                LocalDate.of(1990, 1, 1),
                "carlos.silva@example.com",
                "+5511999998888",
                "StrongPassword123!"
        );

        when(customerRepository.existsByCpf(Cpf.of("52998224725"))).thenReturn(false);
        when(customerRepository.existsByEmail(Email.of("carlos.silva@example.com"))).thenReturn(true);

        assertThatThrownBy(() -> createAccountService.createAccount(command))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("carlos.silva@example.com");

        verify(customerRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        verify(outboxPort, never()).save(any());
    }

    @Test
    @DisplayName("Should reject account creation when customer is under 18 years old")
    void shouldRejectWhenCustomerIsUnderage() {
        CreateAccountCommand command = new CreateAccountCommand(
                "Minor Customer",
                "52998224725",
                LocalDate.of(2015, 1, 1),
                "minor@example.com",
                "+5511999998888",
                "StrongPassword123!"
        );

        when(customerRepository.existsByCpf(Cpf.of("52998224725"))).thenReturn(false);
        when(customerRepository.existsByEmail(Email.of("minor@example.com"))).thenReturn(false);

        assertThatThrownBy(() -> createAccountService.createAccount(command))
                .isInstanceOf(CustomerUnderageException.class);

        verify(customerRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        verify(outboxPort, never()).save(any());
    }
}
