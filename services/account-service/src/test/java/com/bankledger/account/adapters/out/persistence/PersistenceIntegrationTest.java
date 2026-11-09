package com.bankledger.account.adapters.out.persistence;

import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PersistenceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("account_db")
            .withUsername("bankledger")
            .withPassword("bankledger");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private CustomerRepositoryPort customerRepository;

    @Autowired
    private AccountRepositoryPort accountRepository;

    @Autowired
    private AccountSpringDataRepository accountSpringDataRepository;

    @Autowired
    private CustomerSpringDataRepository customerSpringDataRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        accountSpringDataRepository.deleteAll();
        customerSpringDataRepository.deleteAll();
    }

    private Customer buildCustomer(String cpfDigits, String emailStr) {
        return Customer.create(
                UUID.randomUUID(),
                "Maria Oliveira",
                Cpf.of(cpfDigits),
                Email.of(emailStr),
                Phone.of("+5511988887777"),
                LocalDate.of(1990, 5, 15),
                "$2a$12$e80yqTfA2Qd4E9x7o.samplepasswordhash",
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should save and retrieve Customer by ID, CPF, and Email")
    void shouldSaveAndRetrieveCustomer() {
        Customer customer = buildCustomer("52998224725", "maria.oliveira@example.com");

        Customer saved = customerRepository.save(customer);
        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo(customer.id());

        Optional<Customer> byId = customerRepository.findById(customer.id());
        assertThat(byId).isPresent();
        assertThat(byId.get().fullName()).isEqualTo("Maria Oliveira");
        assertThat(byId.get().cpf().value()).isEqualTo("52998224725");
        assertThat(byId.get().email().value()).isEqualTo("maria.oliveira@example.com");
        assertThat(byId.get().phone().value()).isEqualTo("+5511988887777");
        assertThat(byId.get().birthDate()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(byId.get().failedLoginAttempts()).isEqualTo(0);
        assertThat(byId.get().lockedUntil()).isNull();

        Optional<Customer> byCpf = customerRepository.findByCpf(Cpf.of("52998224725"));
        assertThat(byCpf).isPresent();
        assertThat(byCpf.get().id()).isEqualTo(customer.id());

        Optional<Customer> byEmail = customerRepository.findByEmail(Email.of("maria.oliveira@example.com"));
        assertThat(byEmail).isPresent();
        assertThat(byEmail.get().id()).isEqualTo(customer.id());

        assertThat(customerRepository.existsByCpf(Cpf.of("52998224725"))).isTrue();
        assertThat(customerRepository.existsByCpf(Cpf.of("11144477735"))).isFalse();

        assertThat(customerRepository.existsByEmail(Email.of("maria.oliveira@example.com"))).isTrue();
        assertThat(customerRepository.existsByEmail(Email.of("nonexistent@example.com"))).isFalse();
    }

    @Test
    @DisplayName("Should save and retrieve Account by ID, branch/number, and customer ID")
    void shouldSaveAndRetrieveAccount() {
        Customer customer = customerRepository.save(buildCustomer("52998224725", "maria.oliveira@example.com"));

        AccountNumber accountNumber = AccountNumber.of("0001", "100000", "4");
        Account account = Account.create(
                UUID.randomUUID(),
                customer.id(),
                "0001",
                accountNumber,
                Instant.now()
        );

        Account saved = accountRepository.save(account);
        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo(account.id());
        assertThat(saved.status()).isEqualTo(AccountStatus.ACTIVE);

        Optional<Account> byId = accountRepository.findById(account.id());
        assertThat(byId).isPresent();
        assertThat(byId.get().customerId()).isEqualTo(customer.id());
        assertThat(byId.get().branch()).isEqualTo("0001");
        assertThat(byId.get().accountNumber().number()).isEqualTo("100000");
        assertThat(byId.get().accountNumber().checkDigit()).isEqualTo("4");
        assertThat(byId.get().status()).isEqualTo(AccountStatus.ACTIVE);

        Optional<Account> byBranchAndNumber = accountRepository.findByBranchAndNumber("0001", "100000");
        assertThat(byBranchAndNumber).isPresent();
        assertThat(byBranchAndNumber.get().id()).isEqualTo(account.id());

        Optional<Account> byCustomerId = accountRepository.findByCustomerId(customer.id());
        assertThat(byCustomerId).isPresent();
        assertThat(byCustomerId.get().id()).isEqualTo(account.id());
    }

    @Test
    @DisplayName("Should increment account_number_seq on consecutive nextAccountNumber() calls")
    void shouldIncrementSequenceForNextAccountNumber() {
        long seq1 = accountRepository.nextAccountNumber();
        long seq2 = accountRepository.nextAccountNumber();
        long seq3 = accountRepository.nextAccountNumber();

        assertThat(seq1).isGreaterThanOrEqualTo(100000L);
        assertThat(seq2).isEqualTo(seq1 + 1);
        assertThat(seq3).isEqualTo(seq2 + 1);
    }

    @Test
    @DisplayName("Should enforce unique constraint on customer CPF")
    void shouldEnforceUniqueCpfConstraint() {
        Customer customer1 = buildCustomer("52998224725", "user1@example.com");
        customerRepository.save(customer1);

        Customer customer2 = buildCustomer("52998224725", "user2@example.com");

        assertThatThrownBy(() -> customerRepository.save(customer2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce unique constraint on customer email")
    void shouldEnforceUniqueEmailConstraint() {
        Customer customer1 = buildCustomer("52998224725", "shared@example.com");
        customerRepository.save(customer1);

        Customer customer2 = buildCustomer("11144477735", "shared@example.com");

        assertThatThrownBy(() -> customerRepository.save(customer2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce unique constraint on branch and account number")
    void shouldEnforceUniqueBranchAndAccountNumberConstraint() {
        Customer customer1 = customerRepository.save(buildCustomer("52998224725", "user1@example.com"));
        Customer customer2 = customerRepository.save(buildCustomer("11144477735", "user2@example.com"));

        AccountNumber accNum = AccountNumber.of("0001", "100000", "4");
        Account account1 = Account.create(UUID.randomUUID(), customer1.id(), "0001", accNum, Instant.now());
        accountRepository.save(account1);

        Account account2 = Account.create(UUID.randomUUID(), customer2.id(), "0001", accNum, Instant.now());

        assertThatThrownBy(() -> accountRepository.save(account2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce foreign key constraint between account and customer")
    void shouldEnforceForeignKeyConstraint() {
        UUID nonExistentCustomerId = UUID.randomUUID();
        AccountNumber accNum = AccountNumber.of("0001", "100000", "4");
        Account account = Account.create(UUID.randomUUID(), nonExistentCustomerId, "0001", accNum, Instant.now());

        assertThatThrownBy(() -> accountRepository.save(account))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should persist customer updates for failed login attempts and lockout")
    void shouldUpdateCustomerLockout() {
        Customer customer = customerRepository.save(buildCustomer("52998224725", "maria.oliveira@example.com"));
        Instant failureTime = Instant.now();

        customer.recordFailedLogin(failureTime);
        customerRepository.save(customer);

        Customer loaded = customerRepository.findById(customer.id()).orElseThrow();
        assertThat(loaded.failedLoginAttempts()).isEqualTo(1);

        customer.resetFailedLogins(failureTime.plusSeconds(60));
        customerRepository.save(customer);

        Customer resetLoaded = customerRepository.findById(customer.id()).orElseThrow();
        assertThat(resetLoaded.failedLoginAttempts()).isEqualTo(0);
        assertThat(resetLoaded.lockedUntil()).isNull();
    }

    @Test
    @DisplayName("Should persist account closure and updated timestamps")
    void shouldUpdateAccountClosure() {
        Customer customer = customerRepository.save(buildCustomer("52998224725", "maria.oliveira@example.com"));
        AccountNumber accNum = AccountNumber.of("0001", "100000", "4");
        Account account = accountRepository.save(
                Account.create(UUID.randomUUID(), customer.id(), "0001", accNum, Instant.now())
        );

        Instant closeTime = Instant.now().plusSeconds(120);
        account.close(closeTime);
        accountRepository.save(account);

        Account closedLoaded = accountRepository.findById(account.id()).orElseThrow();
        assertThat(closedLoaded.status()).isEqualTo(AccountStatus.CLOSED);
        assertThat(closedLoaded.closedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should accept all valid account statuses according to check constraint")
    void shouldAcceptAllValidAccountStatuses() {
        Customer customer = customerRepository.save(buildCustomer("52998224725", "maria.oliveira@example.com"));

        int i = 0;
        for (AccountStatus status : AccountStatus.values()) {
            UUID accountId = UUID.randomUUID();
            String accNumber = String.format("20000%d", i++);
            int rows = jdbcTemplate.update(
                    "INSERT INTO account (id, customer_id, branch, account_number, check_digit, status, created_at, updated_at) " +
                            "VALUES (?, ?, '0001', ?, '0', ?, now(), now())",
                    accountId, customer.id(), accNumber, status.name()
            );
            assertThat(rows).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Should enforce check constraint on account status column")
    void shouldEnforceCheckConstraintOnAccountStatus() {
        Customer customer = customerRepository.save(buildCustomer("52998224725", "maria.oliveira@example.com"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO account (id, customer_id, branch, account_number, check_digit, status, created_at, updated_at) " +
                        "VALUES (?, ?, '0001', '100000', '4', 'INVALID_STATUS', now(), now())",
                UUID.randomUUID(), customer.id()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
