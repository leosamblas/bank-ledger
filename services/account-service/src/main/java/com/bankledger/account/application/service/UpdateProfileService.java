package com.bankledger.account.application.service;

import com.bankledger.account.application.port.in.AccountMeResult;
import com.bankledger.account.application.port.in.UpdateProfileCommand;
import com.bankledger.account.application.port.in.UpdateProfileUseCase;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.domain.exception.AccountNotFoundException;
import com.bankledger.account.domain.exception.CustomerNotFoundException;
import com.bankledger.account.domain.exception.EmailAlreadyExistsException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.Address;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Dedicated application command service handling customer profile updates.
 * Segregates write operations from CQRS queries according to Hexagonal Architecture.
 */
@Service
@RequiredArgsConstructor
public class UpdateProfileService implements UpdateProfileUseCase {

    private final AccountRepositoryPort accountRepositoryPort;
    private final CustomerRepositoryPort customerRepositoryPort;

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
}
