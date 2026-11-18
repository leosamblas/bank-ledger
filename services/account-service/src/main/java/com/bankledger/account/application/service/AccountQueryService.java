package com.bankledger.account.application.service;

import com.bankledger.account.application.port.in.*;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.domain.exception.AccountNotFoundException;
import com.bankledger.account.domain.exception.CustomerNotFoundException;
import com.bankledger.account.domain.exception.EmailAlreadyExistsException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.Address;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service implementing GetAccountMeUseCase, LookupAccountUseCase, and UpdateProfileUseCase.
 * Handles deterministic name masking and query retrieval.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountQueryService implements GetAccountMeUseCase, LookupAccountUseCase, UpdateProfileUseCase {

    private final AccountRepositoryPort accountRepositoryPort;
    private final CustomerRepositoryPort customerRepositoryPort;

    @Override
    public AccountLookupResult lookup(LookupAccountQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return lookup(query.branch(), query.accountNumber());
    }

    @Override
    public AccountLookupResult lookup(String branch, String accountNumber) {
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");

        String targetBranch = branch.trim().isEmpty() ? Account.DEFAULT_BRANCH : branch.trim();
        String cleanNumber = accountNumber.trim();

        if (cleanNumber.contains("/")) {
            String[] parts = cleanNumber.split("/", 2);
            targetBranch = parts[0].trim();
            cleanNumber = parts[1].trim();
        }

        if (targetBranch.isEmpty()) {
            targetBranch = Account.DEFAULT_BRANCH;
        }

        if (cleanNumber.contains("-")) {
            cleanNumber = cleanNumber.split("-", 2)[0].trim();
        }

        Account account = accountRepositoryPort.findByBranchAndNumber(targetBranch, cleanNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for " + branch + "/" + accountNumber));

        Customer customer = customerRepositoryPort.findById(account.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found for customerId: " + account.getCustomerId()));

        String maskedName = maskHolderName(customer.getFullName());

        return new AccountLookupResult(
                account.getId(),
                account.getBranch(),
                account.getAccountNumber().number(),
                account.getAccountNumber().checkDigit(),
                maskedName
        );
    }

    @Override
    public AccountLookupResult lookup(String searchType, String cpf, String agency, String accountNumber) {
        boolean isCpfSearch = "CPF".equalsIgnoreCase(searchType)
                || (searchType == null && cpf != null && !cpf.isBlank());

        boolean isAccountSearch = "ACCOUNT".equalsIgnoreCase(searchType)
                || (searchType == null && accountNumber != null && !accountNumber.isBlank());

        if (isCpfSearch) {
            if (cpf == null || cpf.isBlank()) {
                throw new IllegalArgumentException("CPF must be provided for CPF lookup");
            }
            String cleanCpf = cpf.trim().replaceAll("\\D", "");
            Cpf cpfVo = Cpf.of(cleanCpf);
            Customer customer = customerRepositoryPort.findByCpf(cpfVo)
                    .orElseThrow(() -> new CustomerNotFoundException("Customer not found for CPF: " + cpf));

            Account account = accountRepositoryPort.findByCustomerId(customer.getId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found for customerId: " + customer.getId()));

            return new AccountLookupResult(
                    account.getId(),
                    account.getBranch(),
                    account.getAccountNumber().number(),
                    account.getAccountNumber().checkDigit(),
                    customer.getFullName(),
                    customer.getCpf().formatted(),
                    "001",
                    "Nexus Bank S.A.",
                    maskHolderName(customer.getFullName())
            );
        } else if (isAccountSearch) {
            if (accountNumber == null || accountNumber.isBlank()) {
                throw new IllegalArgumentException("Account number must be provided for ACCOUNT lookup");
            }
            String targetBranch = (agency != null && !agency.isBlank()) ? agency.trim() : Account.DEFAULT_BRANCH;
            String cleanNumber = accountNumber.trim();

            if (cleanNumber.contains("/")) {
                String[] parts = cleanNumber.split("/", 2);
                targetBranch = parts[0].trim();
                cleanNumber = parts[1].trim();
            }

            if (targetBranch.isEmpty()) {
                targetBranch = Account.DEFAULT_BRANCH;
            }

            if (cleanNumber.contains("-")) {
                cleanNumber = cleanNumber.split("-", 2)[0].trim();
            }

            final String effectiveBranch = targetBranch;
            final String effectiveNumber = cleanNumber;

            Account account = accountRepositoryPort.findByBranchAndNumber(effectiveBranch, effectiveNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found for " + effectiveBranch + "/" + effectiveNumber));

            Customer customer = customerRepositoryPort.findById(account.getCustomerId())
                    .orElseThrow(() -> new CustomerNotFoundException("Customer not found for customerId: " + account.getCustomerId()));

            return new AccountLookupResult(
                    account.getId(),
                    account.getBranch(),
                    account.getAccountNumber().number(),
                    account.getAccountNumber().checkDigit(),
                    customer.getFullName(),
                    customer.getCpf().formatted(),
                    "001",
                    "Nexus Bank S.A.",
                    maskHolderName(customer.getFullName())
            );
        } else {
            throw new IllegalArgumentException("Invalid search parameters: searchType must be CPF or ACCOUNT");
        }
    }

    @Override
    public AccountMeResult getAccountMe(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");

        Account account = accountRepositoryPort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        Customer customer = customerRepositoryPort.findById(account.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found for customerId: " + account.getCustomerId()));

        return new AccountMeResult(
                account.getId(),
                customer.getId(),
                account.getBranch(),
                account.getAccountNumber().number(),
                account.getAccountNumber().checkDigit(),
                account.getStatus(),
                account.getCreatedAt(),
                customer.getFullName(),
                customer.getCpf().value(),
                customer.getEmail().value(),
                customer.getPhone() != null ? customer.getPhone().value() : null,
                customer.getAddress(),
                account.getDailyTransferLimitCents()
        );
    }

    @Override
    public AccountMeResult getAccountByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");

        Account account = accountRepositoryPort.findByCustomerId(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found for customerId: " + customerId));

        Customer customer = customerRepositoryPort.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found for customerId: " + customerId));

        return new AccountMeResult(
                account.getId(),
                customer.getId(),
                account.getBranch(),
                account.getAccountNumber().number(),
                account.getAccountNumber().checkDigit(),
                account.getStatus(),
                account.getCreatedAt(),
                customer.getFullName(),
                customer.getCpf().value(),
                customer.getEmail().value(),
                customer.getPhone() != null ? customer.getPhone().value() : null,
                customer.getAddress(),
                account.getDailyTransferLimitCents()
        );
    }

    @Override
    @Transactional
    public AccountMeResult updateProfile(UpdateProfileCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Account account = accountRepositoryPort.findById(command.accountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + command.accountId()));

        Customer customer = customerRepositoryPort.findById(account.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found for customerId: " + account.getCustomerId()));

        Email newEmail = Email.of(command.email());
        customerRepositoryPort.findByEmail(newEmail).ifPresent(existingCustomer -> {
            if (!existingCustomer.id().equals(customer.id())) {
                throw new EmailAlreadyExistsException("Email already in use: " + command.email());
            }
        });

        Phone newPhone = Phone.of(command.phone());
        Address newAddress = Address.of(
                command.postalCode(),
                command.street(),
                command.number(),
                command.complement(),
                command.neighborhood(),
                command.city(),
                command.state()
        );

        Instant now = Instant.now();
        customer.updateProfile(newPhone, newEmail, newAddress, now);
        Customer savedCustomer = customerRepositoryPort.save(customer);

        return new AccountMeResult(
                account.getId(),
                savedCustomer.getId(),
                account.getBranch(),
                account.getAccountNumber().number(),
                account.getAccountNumber().checkDigit(),
                account.getStatus(),
                account.getCreatedAt(),
                savedCustomer.getFullName(),
                savedCustomer.getCpf().value(),
                savedCustomer.getEmail().value(),
                savedCustomer.getPhone().value(),
                savedCustomer.getAddress(),
                account.getDailyTransferLimitCents()
        );
    }

    /**
     * Deterministically masks customer name keeping first letter visible + 3 asterisks per word.
     * Example: "Carlos Eduardo Silva" -> "C*** E*** S***"
     */
    public static String maskHolderName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(part.charAt(0)).append("***");
            }
        }
        return sb.toString();
    }
}
