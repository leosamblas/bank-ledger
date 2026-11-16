package com.bankledger.auth.application.service;

import com.bankledger.auth.application.port.in.IntrospectCommand;
import com.bankledger.auth.application.port.in.IntrospectResult;
import com.bankledger.auth.application.port.in.IntrospectTokenUseCase;
import com.bankledger.auth.application.port.in.LoginCommand;
import com.bankledger.auth.application.port.in.LoginResult;
import com.bankledger.auth.application.port.in.LoginUseCase;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.auth.application.port.out.AuthUserRepositoryPort;
import com.bankledger.auth.application.port.out.PasswordEncoderPort;
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.auth.domain.exception.CustomerLockedException;
import com.bankledger.auth.domain.exception.InvalidCredentialsException;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
import com.bankledger.commons.security.TokenClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService implements LoginUseCase, RegisterCredentialsUseCase, IntrospectTokenUseCase {

    private static final String DUMMY_ARGON2_HASH =
            "$argon2id$v=19$m=19456,t=3,p=1$A95dCHZGbTCnwE0PsaX0Vw$PpVtTpb8S+tIiPPH7dFQLQqb1quYB17eMUJZI+Mbcz8";

    private final AuthUserRepositoryPort authUserRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final RsaJwtSigner rsaJwtSigner;
    private final RsaJwtValidator rsaJwtValidator;
    private final Clock clock;

    private String sanitizeCpf(String cpf) {
        if (cpf == null) {
            return "";
        }
        return cpf.replaceAll("\\D", "");
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, CustomerLockedException.class})
    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        String sanitizedCpf = sanitizeCpf(command.cpf());
        if (sanitizedCpf.length() != 11) {
            // Timing side-channel defense on malformed CPF
            passwordEncoderPort.matches(command.password() != null ? command.password() : "", DUMMY_ARGON2_HASH);
            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        Instant now = Instant.now(clock);

        Optional<AuthUser> maybeUser = authUserRepositoryPort.findByCpf(sanitizedCpf);
        if (maybeUser.isEmpty()) {
            // Timing side-channel defense: simulate Argon2id password hash check to prevent CPF enumeration
            passwordEncoderPort.matches(command.password() != null ? command.password() : "", DUMMY_ARGON2_HASH);
            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        AuthUser user = maybeUser.get();

        if (user.isLocked(now)) {
            long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
            throw new CustomerLockedException(
                    "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
            );
        }

        boolean matches = passwordEncoderPort.matches(command.password(), user.getPasswordHash());
        if (!matches) {
            user.recordFailedLogin(now);
            authUserRepositoryPort.save(user);

            if (user.isLocked(now)) {
                long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
                throw new CustomerLockedException(
                        "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
                );
            }

            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        user.resetFailedLogins(now);
        authUserRepositoryPort.save(user);

        TokenClaims claims = new TokenClaims(
                user.getCustomerId(),
                user.getAccountId(),
                user.getCpf(),
                user.getRole(),
                user.getBranch(),
                user.getAccountNumber()
        );

        String token = rsaJwtSigner.generateToken(claims);

        return new LoginResult(
                token,
                3600L,
                "Bearer",
                new LoginResult.UserInfo(
                        user.getCustomerId(),
                        user.getFullName(),
                        user.getCpf(),
                        user.getEmail()
                )
        );
    }

    @Override
    @Transactional
    public AuthUser registerCredentials(RegisterCredentialsCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        String sanitizedCpf = sanitizeCpf(command.cpf());
        if (sanitizedCpf.length() != 11) {
            throw new InvalidCredentialsException("CPF inválido: deve conter exatamente 11 dígitos");
        }

        if (authUserRepositoryPort.existsByCpf(sanitizedCpf)) {
            throw new CredentialsAlreadyExistsException("Credenciais já cadastradas para o CPF: " + sanitizedCpf);
        }

        Instant now = Instant.now(clock);
        String hashedPassword = passwordEncoderPort.encode(command.password());

        AuthUser user = new AuthUser(
                UUID.randomUUID(),
                sanitizedCpf,
                hashedPassword,
                "ROLE_USER",
                command.customerId(),
                command.accountId(),
                command.branch(),
                command.accountNumber(),
                0,
                null,
                command.fullName(),
                command.email(),
                now,
                now
        );

        return authUserRepositoryPort.save(user);
    }

    @Override
    public IntrospectResult introspect(IntrospectCommand command) {
        if (command == null || command.token() == null || command.token().isBlank()) {
            return IntrospectResult.inactive();
        }

        try {
            TokenClaims claims = rsaJwtValidator.validate(command.token());
            String sub = claims.cpf() != null ? claims.cpf() : (claims.customerId() != null ? claims.customerId().toString() : null);
            return new IntrospectResult(
                    true,
                    sub,
                    claims.customerId(),
                    claims.accountId(),
                    claims.branch(),
                    claims.accountNumber(),
                    claims.role(),
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            return IntrospectResult.inactive();
        }
    }

    private long calculateMinutesLeft(Instant lockedUntil, Instant now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, lockedUntil).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }
}
