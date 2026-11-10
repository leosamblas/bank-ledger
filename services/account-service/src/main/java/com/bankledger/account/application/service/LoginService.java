package com.bankledger.account.application.service;

import com.bankledger.account.application.port.in.LoginCommand;
import com.bankledger.account.application.port.in.LoginResult;
import com.bankledger.account.application.port.in.LoginUseCase;
import com.bankledger.account.application.port.out.AccountRepositoryPort;
import com.bankledger.account.application.port.out.CustomerRepositoryPort;
import com.bankledger.account.application.port.out.PasswordEncoderPort;
import com.bankledger.account.application.port.out.TokenServicePort;
import com.bankledger.account.domain.exception.AccountNotFoundException;
import com.bankledger.account.domain.exception.CustomerLockedException;
import com.bankledger.account.domain.exception.InvalidCredentialsException;
import com.bankledger.account.domain.exception.InvalidCpfException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.Cpf;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Application service implementing LoginUseCase.
 * Handles authentication, attempt counting, lockout rules, and token generation.
 */
@Service
@Transactional(noRollbackFor = {InvalidCredentialsException.class, CustomerLockedException.class})
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final CustomerRepositoryPort customerRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final TokenServicePort tokenServicePort;
    private final Clock clock;

    @Override
    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Instant now = Instant.now(clock);

        Customer customer;
        try {
            customer = customerRepositoryPort.findByCpf(Cpf.of(command.cpf()))
                    .orElseThrow(() -> new InvalidCredentialsException("Credenciais inválidas"));
        } catch (InvalidCpfException e) {
            throw new InvalidCredentialsException("Credenciais inválidas", e);
        }

        if (customer.isLocked(now)) {
            long minutesLeft = calculateMinutesLeft(customer.getLockedUntil(), now);
            throw new CustomerLockedException(
                    "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
            );
        }

        boolean matches = passwordEncoderPort.matches(command.password(), customer.getPasswordHash());
        if (!matches) {
            customer.recordFailedLogin(now);
            customerRepositoryPort.save(customer);

            if (customer.isLocked(now)) {
                long minutesLeft = calculateMinutesLeft(customer.getLockedUntil(), now);
                throw new CustomerLockedException(
                        "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
                );
            }

            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        customer.resetFailedLogins(now);
        customerRepositoryPort.save(customer);

        Account account = accountRepositoryPort.findByCustomerId(customer.getId())
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new AccountNotFoundException("Active account not found for customer: " + customer.getId()));

        String token = tokenServicePort.generateToken(customer, account);

        return new LoginResult(
                token,
                3600L,
                customer.getId(),
                account.getId(),
                account.getBranch(),
                account.getAccountNumber().number(),
                customer.getFullName()
        );
    }

    private long calculateMinutesLeft(Instant lockedUntil, Instant now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, lockedUntil).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }
}
