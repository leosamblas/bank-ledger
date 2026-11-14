package com.bankledger.account.domain.event;

import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.commons.constant.KafkaTopics;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain record representing the AccountOpened business event.
 * Published to transactional outbox and Kafka topic 'account.events.v1'.
 * Zero framework dependencies.
 */
public record AccountOpenedEvent(
        UUID accountId,
        UUID customerId,
        String customerCpf,
        String customerName,
        String branch,
        String accountNumber,
        String checkDigit,
        Instant openedAt
) implements Serializable {

    public static final String EVENT_TYPE = "AccountOpened";
    public static final String TOPIC = KafkaTopics.ACCOUNT_EVENTS_V1;

    public AccountOpenedEvent {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(customerCpf, "customerCpf must not be null");
        Objects.requireNonNull(customerName, "customerName must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(checkDigit, "checkDigit must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
    }

    public static AccountOpenedEvent of(Account account, Customer customer, Instant openedAt) {
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");

        return new AccountOpenedEvent(
                account.getId(),
                customer.getId(),
                customer.getCpf().value(),
                customer.getFullName(),
                account.getBranch(),
                account.getAccountNumber().number(),
                account.getAccountNumber().checkDigit(),
                openedAt
        );
    }

    public static AccountOpenedEvent of(Account account, Customer customer) {
        return of(account, customer, account.getCreatedAt());
    }
}
