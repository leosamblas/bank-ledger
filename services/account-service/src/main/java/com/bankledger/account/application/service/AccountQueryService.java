package com.bankledger.account.application.service;

import com.bankledger.account.application.port.in.*;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.domain.exception.AccountNotFoundException;
import com.bankledger.account.domain.exception.CustomerNotFoundException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Application service implementing GetAccountMeUseCase and LookupAccountUseCase.
 * Handles deterministic name masking and query retrieval.
 */
@Service
@Transactional(readOnly = true)
public class AccountQueryService implements GetAccountMeUseCase, LookupAccountUseCase {

    private final AccountRepositoryPort accountRepositoryPort;
    private final CustomerRepositoryPort customerRepositoryPort;

    public AccountQueryService(AccountRepositoryPort accountRepositoryPort, CustomerRepositoryPort customerRepositoryPort) {
        this.accountRepositoryPort = Objects.requireNonNull(accountRepositoryPort, "accountRepositoryPort must not be null");
        this.customerRepositoryPort = Objects.requireNonNull(customerRepositoryPort, "customerRepositoryPort must not be null");
    }

    @Override
    public AccountLookupResult lookup(LookupAccountQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return lookup(query.branch(), query.accountNumber());
    }

    @Override
    public AccountLookupResult lookup(String branch, String accountNumber) {
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");

        String targetBranch = branch.trim();
        String cleanNumber = accountNumber.trim();

        if (cleanNumber.contains("/")) {
            String[] parts = cleanNumber.split("/", 2);
            targetBranch = parts[0].trim();
            cleanNumber = parts[1].trim();
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
                customer.getEmail().value()
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
                customer.getEmail().value()
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
