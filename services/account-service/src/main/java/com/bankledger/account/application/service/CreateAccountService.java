package com.bankledger.account.application.service;

import com.bankledger.account.application.port.in.CreateAccountCommand;
import com.bankledger.account.application.port.in.CreateAccountUseCase;
import com.bankledger.account.application.port.in.CreatedAccountResult;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.application.port.out.OutboxPort;
import com.bankledger.account.application.port.out.PasswordEncoderPort;
import com.bankledger.account.domain.event.AccountOpenedEvent;
import com.bankledger.account.domain.exception.CpfAlreadyExistsException;
import com.bankledger.account.domain.exception.EmailAlreadyExistsException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.AccountNumber;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;
import com.bankledger.commons.outbox.OutboxEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for opening a new bank account.
 * Atomically creates Customer, Account, and records OutboxEvent.
 *
 * <p><strong>Note on Lifecycle:</strong> In Slice 1, newly opened accounts are initialized with
 * {@code ACTIVE} status to allow direct end-to-end testing without ledger dependency. This will be
 * switched to {@code PENDING_ACTIVATION} when the {@code LedgerAccountProvisioned} saga is introduced
 * in Slice 2.</p>
 */
@Service
@Transactional
public class CreateAccountService implements CreateAccountUseCase {

    private final CustomerRepositoryPort customerRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final OutboxPort outboxPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CreateAccountService(
            CustomerRepositoryPort customerRepositoryPort,
            AccountRepositoryPort accountRepositoryPort,
            PasswordEncoderPort passwordEncoderPort,
            OutboxPort outboxPort,
            ObjectMapper objectMapper
    ) {
        this(customerRepositoryPort, accountRepositoryPort, passwordEncoderPort, outboxPort, objectMapper, Clock.systemUTC());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CreateAccountService(
            CustomerRepositoryPort customerRepositoryPort,
            AccountRepositoryPort accountRepositoryPort,
            PasswordEncoderPort passwordEncoderPort,
            OutboxPort outboxPort,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.customerRepositoryPort = Objects.requireNonNull(customerRepositoryPort, "customerRepositoryPort must not be null");
        this.accountRepositoryPort = Objects.requireNonNull(accountRepositoryPort, "accountRepositoryPort must not be null");
        this.passwordEncoderPort = Objects.requireNonNull(passwordEncoderPort, "passwordEncoderPort must not be null");
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public CreatedAccountResult createAccount(CreateAccountCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Instant now = Instant.now(clock);

        Cpf cpf = Cpf.of(command.cpf());
        if (customerRepositoryPort.existsByCpf(cpf)) {
            throw new CpfAlreadyExistsException("Customer already exists with CPF: " + command.cpf());
        }

        Email email = Email.of(command.email());
        if (customerRepositoryPort.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Customer already exists with email: " + command.email());
        }

        String encodedPassword = passwordEncoderPort.encode(command.password());

        Customer customer = Customer.create(
                UUID.randomUUID(),
                command.fullName(),
                cpf,
                email,
                Phone.of(command.phone()),
                command.birthDate(),
                encodedPassword,
                now
        );

        long nextSeq = accountRepositoryPort.nextAccountNumber();
        AccountNumber accountNumber = AccountNumber.ofBranchAndNumber(AccountNumber.DEFAULT_BRANCH, String.format("%06d", nextSeq));

        Account account = Account.create(
                customer.getId(),
                accountNumber,
                now
        );

        AccountOpenedEvent openedEvent = AccountOpenedEvent.of(account, customer, now);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(openedEvent);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize AccountOpenedEvent to JSON", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.pending(
                "Account",
                account.getId(),
                AccountOpenedEvent.EVENT_TYPE,
                AccountOpenedEvent.TOPIC,
                account.getId().toString(),
                payload
        );

        Customer savedCustomer = customerRepositoryPort.save(customer);
        Account savedAccount = accountRepositoryPort.save(account);
        outboxPort.save(outboxEvent);

        return new CreatedAccountResult(
                savedAccount.getId(),
                savedCustomer.getId(),
                savedAccount.getBranch(),
                savedAccount.getAccountNumber().number(),
                savedAccount.getAccountNumber().checkDigit(),
                savedAccount.getStatus(),
                savedAccount.getCreatedAt()
        );
    }
}
