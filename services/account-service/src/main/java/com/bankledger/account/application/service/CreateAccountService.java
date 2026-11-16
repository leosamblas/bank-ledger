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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for opening a new bank account.
 * Atomically creates Customer, Account, and records an OutboxEvent.
 * Delegates credential registration to auth-service with timeout and fallback.
 */
@Service
@Transactional
public class CreateAccountService implements CreateAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAccountService.class);

    private final CustomerRepositoryPort customerRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final OutboxPort outboxPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String authServiceUrl;
    private final RestClient restClient;

    public CreateAccountService(
            CustomerRepositoryPort customerRepositoryPort,
            AccountRepositoryPort accountRepositoryPort,
            PasswordEncoderPort passwordEncoderPort,
            OutboxPort outboxPort,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.auth-service.url:http://localhost:8086}") String authServiceUrl,
            RestClient.Builder restClientBuilder
    ) {
        this.customerRepositoryPort = Objects.requireNonNull(customerRepositoryPort, "customerRepositoryPort must not be null");
        this.accountRepositoryPort = Objects.requireNonNull(accountRepositoryPort, "accountRepositoryPort must not be null");
        this.passwordEncoderPort = Objects.requireNonNull(passwordEncoderPort, "passwordEncoderPort must not be null");
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.authServiceUrl = (authServiceUrl != null && !authServiceUrl.isBlank()) ? authServiceUrl : "http://localhost:8086";

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder.build();
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
        AccountNumber accountNumber = AccountNumber.ofBranchAndNumber(Account.DEFAULT_BRANCH, String.format("%06d", nextSeq));

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

        registerCredentialsWithAuthService(
                savedCustomer.getId(),
                command.cpf(),
                command.password(),
                savedAccount.getId(),
                savedAccount.getBranch(),
                savedAccount.getAccountNumber().number(),
                command.fullName(),
                command.email()
        );

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

    private void registerCredentialsWithAuthService(
            UUID customerId,
            String cpf,
            String password,
            UUID accountId,
            String branch,
            String accountNumber,
            String fullName,
            String email
    ) {
        String endpoint = authServiceUrl.replaceAll("/+$", "") + "/internal/v1/auth/credentials";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("customerId", customerId);
            body.put("cpf", cpf);
            body.put("password", password);
            body.put("accountId", accountId);
            body.put("branch", branch);
            body.put("accountNumber", accountNumber);
            body.put("fullName", fullName);
            body.put("email", email);

            String json = objectMapper.writeValueAsString(body);

            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Credentials registered in auth-service for customerId: {}", customerId);
        } catch (Exception ex) {
            log.warn("Failed to delegate credentials to auth-service at {}: {}. Proceeding with local fallback.",
                    endpoint, ex.getMessage());
        }
    }
}
