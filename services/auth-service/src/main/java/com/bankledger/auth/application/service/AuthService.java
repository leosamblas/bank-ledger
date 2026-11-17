package com.bankledger.auth.application.service;

import com.bankledger.auth.application.port.in.FindAuthUserUseCase;
import com.bankledger.auth.application.port.in.IntrospectCommand;
import com.bankledger.auth.application.port.in.IntrospectResult;
import com.bankledger.auth.application.port.in.IntrospectTokenUseCase;
import com.bankledger.auth.application.port.in.LoginCommand;
import com.bankledger.auth.application.port.in.LoginResult;
import com.bankledger.auth.application.port.in.LoginUseCase;
import com.bankledger.auth.application.port.in.RefreshTokenCommand;
import com.bankledger.auth.application.port.in.RefreshTokenUseCase;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.auth.application.port.out.AccountLockoutPort;
import com.bankledger.auth.application.port.out.AccountReadyEventPublisherPort;
import com.bankledger.auth.application.port.out.AuthUserRepositoryPort;
import com.bankledger.auth.application.port.out.PasswordEncoderPort;
import com.bankledger.auth.domain.event.AccountReadyEvent;
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.auth.domain.exception.CustomerLockedException;
import com.bankledger.auth.domain.exception.InvalidCredentialsException;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
import com.bankledger.commons.security.SecurityConstants;
import com.bankledger.commons.security.TokenClaims;
import com.bankledger.auth.config.AuthSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements LoginUseCase, RefreshTokenUseCase, RegisterCredentialsUseCase, IntrospectTokenUseCase, FindAuthUserUseCase {

    private final AuthUserRepositoryPort authUserRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final RsaJwtSigner rsaJwtSigner;
    private final RsaJwtValidator rsaJwtValidator;
    private final Clock clock;
    private final Optional<AccountLockoutPort> accountLockoutPort;
    private final Optional<AccountReadyEventPublisherPort> accountReadyEventPublisherPort;
    private final AuthSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private String sanitizeCpf(String cpf) {
        if (cpf == null) {
            return "";
        }
        return cpf.replaceAll("\\D", "");
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, CustomerLockedException.class})
    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        String sanitizedCpf = sanitizeCpf(command.cpf());
        if (sanitizedCpf.length() != 11) {
            // Timing side-channel defense on malformed CPF
            passwordEncoderPort.matches(command.password() != null ? command.password() : "", properties.getEffectiveDummyHash());
            log.warn("Login rejected: CPF is not 11 digits");
            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        // Fast account lockout in Redis BEFORE querying database
        if (accountLockoutPort.isPresent() && accountLockoutPort.get().isLocked(sanitizedCpf)) {
            log.warn("Login rejected: account fast-locked in Redis for CPF: {}", sanitizedCpf);
            throw new CustomerLockedException(
                    "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em 15 minutos."
            );
        }

        Instant now = Instant.now(clock);

        Optional<AuthUser> maybeUser = authUserRepositoryPort.findByCpf(sanitizedCpf);
        if (maybeUser.isEmpty()) {
            // Timing side-channel defense: simulate password hash check to prevent CPF enumeration
            passwordEncoderPort.matches(command.password() != null ? command.password() : "", properties.getEffectiveDummyHash());
            log.warn("Login rejected: user not found for CPF");
            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        AuthUser user = maybeUser.get();

        if (user.isLocked(now)) {
            long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
            log.warn("Login rejected: account locked for customerId: {}, minutes left: {}", user.getCustomerId(), minutesLeft);
            throw new CustomerLockedException(
                    "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
            );
        }

        boolean matches = passwordEncoderPort.matches(command.password(), user.getPasswordHash());
        if (!matches) {
            user.recordFailedLogin(now);
            authUserRepositoryPort.save(user);

            long redisAttempts = accountLockoutPort
                    .map(port -> port.recordFailedAttempt(sanitizedCpf))
                    .orElse(0L);

            if (redisAttempts >= 5 || user.isLocked(now)) {
                long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
                if (minutesLeft <= 0) {
                    minutesLeft = 15;
                }
                log.warn("Account newly locked after 5 failed attempts for customerId: {}", user.getCustomerId());
                throw new CustomerLockedException(
                        "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
                );
            }

            log.warn("Login rejected: invalid password for customerId: {}. Failed attempts: {}",
                    user.getCustomerId(), user.getFailedLoginAttempts());
            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        // Reset failed attempts in Redis on successful login
        accountLockoutPort.ifPresent(port -> port.resetFailedAttempts(sanitizedCpf));

        user.resetFailedLogins(now);

        // Security rule: if user already has an active unexpired token, reuse it with dynamically calculated remaining TTL
        if (user.hasActiveToken(now)) {
            long remainingSeconds = Duration.between(now, user.getTokenExpiresAt()).getSeconds();
            if (remainingSeconds > 0) {
                authUserRepositoryPort.save(user);
                log.info("Active token reused for customerId: {} with remaining TTL: {}s", user.getCustomerId(), remainingSeconds);
                return new LoginResult(
                        user.getActiveToken(),
                        remainingSeconds,
                        HttpHeaderConstants.BEARER_PREFIX.trim(),
                        user.getRefreshToken(),
                        new LoginResult.UserInfo(
                                user.getCustomerId(),
                                user.getFullName(),
                                user.getCpf(),
                                user.getEmail()
                        )
                );
            }
        }

        // Generate fresh access token and refresh token
        TokenClaims claims = new TokenClaims(
                user.getCustomerId(),
                user.getAccountId(),
                user.getCpf(),
                user.getRole(),
                user.getBranch(),
                user.getAccountNumber()
        );

        String token = rsaJwtSigner.generateToken(claims);
        String refreshToken = generateSecureToken();
        Instant tokenExpiresAt = now.plusSeconds(properties.getAccessTokenValiditySeconds());
        Instant refreshTokenExpiresAt = now.plusSeconds(properties.getRefreshTokenValiditySeconds());

        user.updateTokens(token, tokenExpiresAt, refreshToken, refreshTokenExpiresAt, now);
        authUserRepositoryPort.save(user);
        log.info("Fresh tokens generated and issued for customerId: {}", user.getCustomerId());

        return new LoginResult(
                token,
                properties.getAccessTokenValiditySeconds(),
                HttpHeaderConstants.BEARER_PREFIX.trim(),
                refreshToken,
                new LoginResult.UserInfo(
                        user.getCustomerId(),
                        user.getFullName(),
                        user.getCpf(),
                        user.getEmail()
                )
        );
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, CustomerLockedException.class})
    public LoginResult refreshToken(RefreshTokenCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        if (command.refreshToken() == null || command.refreshToken().isBlank()) {
            throw new InvalidCredentialsException("Token de atualização inválido ou inexistente");
        }

        Instant now = Instant.now(clock);
        Optional<AuthUser> maybeUser = authUserRepositoryPort.findByRefreshToken(command.refreshToken().trim());
        if (maybeUser.isEmpty()) {
            log.warn("Refresh token not found or invalid");
            throw new InvalidCredentialsException("Token de atualização inválido ou inexistente");
        }

        AuthUser user = maybeUser.get();
        if (user.isLocked(now)) {
            long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
            log.warn("Refresh rejected: account locked for customerId: {}", user.getCustomerId());
            throw new CustomerLockedException(
                    "Conta bloqueada temporariamente. Tente novamente em " + minutesLeft + " minutos."
            );
        }

        if (user.getRefreshTokenExpiresAt() == null || !user.getRefreshTokenExpiresAt().isAfter(now)) {
            log.warn("Refresh rejected: refresh token expired for customerId: {}", user.getCustomerId());
            throw new InvalidCredentialsException("Token de atualização expirado. Efetue login novamente.");
        }

        TokenClaims claims = new TokenClaims(
                user.getCustomerId(),
                user.getAccountId(),
                user.getCpf(),
                user.getRole(),
                user.getBranch(),
                user.getAccountNumber()
        );

        String newAccessToken = rsaJwtSigner.generateToken(claims);
        String newRefreshToken = generateSecureToken();
        Instant tokenExpiresAt = now.plusSeconds(properties.getAccessTokenValiditySeconds());
        Instant refreshTokenExpiresAt = now.plusSeconds(properties.getRefreshTokenValiditySeconds());

        user.updateTokens(newAccessToken, tokenExpiresAt, newRefreshToken, refreshTokenExpiresAt, now);
        authUserRepositoryPort.save(user);
        log.info("Token rotated and refreshed for customerId: {}", user.getCustomerId());

        return new LoginResult(
                newAccessToken,
                properties.getAccessTokenValiditySeconds(),
                HttpHeaderConstants.BEARER_PREFIX.trim(),
                newRefreshToken,
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
            log.warn("Registration rejected: CPF already exists");
            throw new CredentialsAlreadyExistsException("Credenciais já cadastradas para o CPF: " + sanitizedCpf);
        }

        Instant now = Instant.now(clock);
        String hashedPassword = passwordEncoderPort.encode(command.password());

        AuthUser user = new AuthUser(
                UUID.randomUUID(),
                sanitizedCpf,
                hashedPassword,
                SecurityConstants.ROLE_USER,
                command.customerId(),
                command.accountId(),
                command.branch(),
                command.accountNumber(),
                0,
                null,
                command.fullName(),
                command.email(),
                null,
                null,
                null,
                null,
                now,
                now
        );

        AuthUser saved = authUserRepositoryPort.save(user);
        log.info("Credentials registered successfully for customerId: {}", saved.getCustomerId());

        // Publish outbound AccountReadyEvent wrapped in KafkaEventEnvelope to account.ready.v1
        accountReadyEventPublisherPort.ifPresent(port -> {
            AccountReadyPayload payload = new AccountReadyPayload(
                    saved.getCustomerId(),
                    saved.getAccountId() != null ? saved.getAccountId() : UUID.randomUUID(),
                    saved.getEmail() != null ? saved.getEmail() : "user@bank.internal",
                    saved.getFullName() != null ? saved.getFullName() : "Customer",
                    saved.getBranch() != null ? saved.getBranch() : "0001",
                    saved.getAccountNumber() != null ? saved.getAccountNumber() : "000000",
                    now
            );
            port.publish(AccountReadyEvent.of(payload, now));
        });

        return saved;
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
            log.debug("Token introspection failed: {}", e.getMessage());
            return IntrospectResult.inactive();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthUser> findByCustomerId(UUID customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        return authUserRepositoryPort.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthUser> findByCpf(String cpf) {
        String sanitized = sanitizeCpf(cpf);
        if (sanitized.length() != 11) {
            return Optional.empty();
        }
        return authUserRepositoryPort.findByCpf(sanitized);
    }

    private long calculateMinutesLeft(Instant lockedUntil, Instant now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, lockedUntil).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }
}
