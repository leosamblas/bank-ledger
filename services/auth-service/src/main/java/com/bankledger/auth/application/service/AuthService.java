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
import com.bankledger.auth.config.AuthSecurityProperties;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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

    private long calculateMinutesLeft(Instant lockedUntil, Instant now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1);
    }

    @Override
    public Mono<LoginResult> login(LoginCommand command) {
        if (command == null) {
            return Mono.error(new NullPointerException("command must not be null"));
        }

        String sanitizedCpf = sanitizeCpf(command.cpf());
        if (sanitizedCpf.length() != 11) {
            // Timing side-channel defense on malformed CPF
            return Mono.fromCallable(() -> passwordEncoderPort.matches(command.password() != null ? command.password() : "", properties.getEffectiveDummyHash()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then(Mono.defer(() -> {
                        log.warn("Login rejected: CPF is not 11 digits");
                        return Mono.error(new InvalidCredentialsException("Credenciais inválidas"));
                    }));
        }

        // Fast account lockout check in Redis BEFORE querying database
        Mono<Boolean> isLockedMono = accountLockoutPort
                .map(port -> port.isLocked(sanitizedCpf))
                .orElseGet(() -> Mono.just(false));

        return isLockedMono.flatMap(locked -> {
            if (Boolean.TRUE.equals(locked)) {
                log.warn("Login rejected: account fast-locked in Redis for CPF: {}", sanitizedCpf);
                return Mono.error(new CustomerLockedException(
                        "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em 15 minutos."
                ));
            }

            Instant now = Instant.now(clock);

            return authUserRepositoryPort.findByCpf(sanitizedCpf)
                    .switchIfEmpty(Mono.defer(() -> {
                        // Timing side-channel defense: simulate password hash check to prevent CPF enumeration
                        return Mono.fromCallable(() -> passwordEncoderPort.matches(command.password() != null ? command.password() : "", properties.getEffectiveDummyHash()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then(Mono.defer(() -> {
                                    log.warn("Login rejected: user not found for CPF");
                                    return Mono.error(new InvalidCredentialsException("Credenciais inválidas"));
                                }));
                    }))
                    .flatMap(user -> {
                        if (user.isLocked(now)) {
                            long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
                            log.warn("Login rejected: account locked for customerId: {}, minutes left: {}", user.getCustomerId(), minutesLeft);
                            return Mono.error(new CustomerLockedException(
                                    "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
                            ));
                        }

                        return Mono.fromCallable(() -> passwordEncoderPort.matches(command.password(), user.getPasswordHash()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(matches -> {
                                    if (!Boolean.TRUE.equals(matches)) {
                                        user.recordFailedLogin(now);
                                        Mono<Long> recordAttemptMono = accountLockoutPort
                                                .map(port -> port.recordFailedAttempt(sanitizedCpf))
                                                .orElseGet(() -> Mono.just(0L));

                                        return authUserRepositoryPort.save(user)
                                                .then(recordAttemptMono)
                                                .flatMap(redisAttempts -> {
                                                    if (redisAttempts >= 5 || user.isLocked(now)) {
                                                        long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
                                                        if (minutesLeft <= 0) {
                                                            minutesLeft = 15;
                                                        }
                                                        log.warn("Account newly locked after 5 failed attempts for customerId: {}", user.getCustomerId());
                                                        return Mono.error(new CustomerLockedException(
                                                                "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
                                                        ));
                                                    }

                                                    log.warn("Login rejected: invalid password for customerId: {}. Failed attempts: {}",
                                                            user.getCustomerId(), user.getFailedLoginAttempts());
                                                    return Mono.error(new InvalidCredentialsException("Credenciais inválidas"));
                                                });
                                    }

                                    // Successful login: reset failed attempts in Redis
                                    Mono<Void> resetMono = accountLockoutPort
                                            .map(port -> port.resetFailedAttempts(sanitizedCpf))
                                            .orElseGet(Mono::empty);

                                    return resetMono.then(Mono.defer(() -> {
                                        user.resetFailedLogins(now);

                                        // If user already has an active unexpired token, reuse it
                                        if (user.hasActiveToken(now)) {
                                            long remainingSeconds = Duration.between(now, user.getTokenExpiresAt()).getSeconds();
                                            if (remainingSeconds > 0) {
                                                return authUserRepositoryPort.save(user)
                                                        .map(saved -> {
                                                            log.info("Active token reused for customerId: {} with remaining TTL: {}s", saved.getCustomerId(), remainingSeconds);
                                                            return new LoginResult(
                                                                    saved.getActiveToken(),
                                                                    remainingSeconds,
                                                                    HttpHeaderConstants.BEARER_PREFIX.trim(),
                                                                    saved.getRefreshToken(),
                                                                    new LoginResult.UserInfo(
                                                                            saved.getCustomerId(),
                                                                            saved.getFullName(),
                                                                            saved.getCpf(),
                                                                            saved.getEmail()
                                                                    )
                                                            );
                                                        });
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
                                        return authUserRepositoryPort.save(user)
                                                .map(saved -> {
                                                    log.info("Fresh tokens generated and issued for customerId: {}", saved.getCustomerId());
                                                    return new LoginResult(
                                                            token,
                                                            properties.getAccessTokenValiditySeconds(),
                                                            HttpHeaderConstants.BEARER_PREFIX.trim(),
                                                            refreshToken,
                                                            new LoginResult.UserInfo(
                                                                    saved.getCustomerId(),
                                                                    saved.getFullName(),
                                                                    saved.getCpf(),
                                                                    saved.getEmail()
                                                                )
                                                    );
                                                });
                                    }));
                                });
                    });
        });
    }

    @Override
    public Mono<LoginResult> refreshToken(RefreshTokenCommand command) {
        if (command == null) {
            return Mono.error(new NullPointerException("command must not be null"));
        }
        if (command.refreshToken() == null || command.refreshToken().isBlank()) {
            return Mono.error(new InvalidCredentialsException("Token de atualização inválido ou inexistente"));
        }

        Instant now = Instant.now(clock);
        return authUserRepositoryPort.findByRefreshToken(command.refreshToken().trim())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Refresh token not found or invalid");
                    return Mono.error(new InvalidCredentialsException("Token de atualização inválido ou inexistente"));
                }))
                .flatMap(user -> {
                    if (user.isLocked(now)) {
                        long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
                        log.warn("Refresh rejected: account locked for customerId: {}", user.getCustomerId());
                        return Mono.error(new CustomerLockedException(
                                "Conta bloqueada temporariamente. Tente novamente em " + minutesLeft + " minutos."
                        ));
                    }

                    if (user.getRefreshTokenExpiresAt() == null || !user.getRefreshTokenExpiresAt().isAfter(now)) {
                        log.warn("Refresh rejected: refresh token expired for customerId: {}", user.getCustomerId());
                        return Mono.error(new InvalidCredentialsException("Token de atualização expirado. Efetue login novamente."));
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
                    return authUserRepositoryPort.save(user)
                            .map(saved -> {
                                log.info("Token rotated and refreshed for customerId: {}", saved.getCustomerId());
                                return new LoginResult(
                                        newAccessToken,
                                        properties.getAccessTokenValiditySeconds(),
                                        HttpHeaderConstants.BEARER_PREFIX.trim(),
                                        newRefreshToken,
                                        new LoginResult.UserInfo(
                                                saved.getCustomerId(),
                                                saved.getFullName(),
                                                saved.getCpf(),
                                                saved.getEmail()
                                        )
                                );
                            });
                });
    }

    @Override
    public Mono<AuthUser> registerCredentials(RegisterCredentialsCommand command) {
        if (command == null) {
            return Mono.error(new NullPointerException("command must not be null"));
        }

        String sanitizedCpf = sanitizeCpf(command.cpf());
        if (sanitizedCpf.length() != 11) {
            return Mono.error(new InvalidCredentialsException("CPF inválido: deve conter exatamente 11 dígitos"));
        }

        return authUserRepositoryPort.existsByCpf(sanitizedCpf)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("Registration rejected: CPF already exists");
                        return Mono.error(new CredentialsAlreadyExistsException("Credenciais já cadastradas para o CPF: " + sanitizedCpf));
                    }

                    Instant now = Instant.now(clock);
                    return Mono.fromCallable(() -> passwordEncoderPort.encode(command.password()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(hashedPassword -> {
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

                                return authUserRepositoryPort.save(user)
                                        .flatMap(saved -> {
                                            log.info("Credentials registered successfully for customerId: {}", saved.getCustomerId());

                                            if (accountReadyEventPublisherPort.isPresent()) {
                                                AccountReadyPayload payload = new AccountReadyPayload(
                                                        saved.getCustomerId(),
                                                        saved.getAccountId() != null ? saved.getAccountId() : UUID.randomUUID(),
                                                        saved.getEmail() != null ? saved.getEmail() : "user@bank.internal",
                                                        saved.getFullName() != null ? saved.getFullName() : "Customer",
                                                        saved.getBranch() != null ? saved.getBranch() : "0001",
                                                        saved.getAccountNumber() != null ? saved.getAccountNumber() : "000000",
                                                        now
                                                );
                                                return accountReadyEventPublisherPort.get()
                                                        .publish(AccountReadyEvent.of(payload, now))
                                                        .thenReturn(saved);
                                            }
                                            return Mono.just(saved);
                                        });
                            });
                });
    }

    @Override
    public Mono<IntrospectResult> introspect(IntrospectCommand command) {
        if (command == null || command.token() == null || command.token().isBlank()) {
            return Mono.just(IntrospectResult.inactive());
        }

        return Mono.fromCallable(() -> {
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
        }).onErrorResume(e -> {
            log.debug("Token introspection failed: {}", e.getMessage());
            return Mono.just(IntrospectResult.inactive());
        });
    }

    @Override
    public Mono<AuthUser> findByCustomerId(UUID customerId) {
        if (customerId == null) {
            return Mono.empty();
        }
        return authUserRepositoryPort.findByCustomerId(customerId);
    }

    @Override
    public Mono<AuthUser> findByCpf(String cpf) {
        String sanitizedCpf = sanitizeCpf(cpf);
        if (sanitizedCpf.length() != 11) {
            return Mono.empty();
        }
        return authUserRepositoryPort.findByCpf(sanitizedCpf);
    }
}
