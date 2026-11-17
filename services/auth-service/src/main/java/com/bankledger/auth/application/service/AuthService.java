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

/**
 * Serviço central de autenticação reativo do Auth-Service.
 * Implementa os casos de uso de login, renovação de tokens (refresh), registro de credenciais,
 * introspecção e busca de usuários sem operações bloqueantes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements LoginUseCase, RefreshTokenUseCase, RegisterCredentialsUseCase, IntrospectTokenUseCase, FindAuthUserUseCase {

    private static final String INVALID_CREDENTIALS_MSG = "Credenciais inválidas";
    private static final String REFRESH_TOKEN_INVALID_MSG = "Token de atualização inválido ou inexistente";
    private static final String REFRESH_TOKEN_EXPIRED_MSG = "Token de atualização expirado. Efetue login novamente.";

    private final AuthUserRepositoryPort authUserRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final RsaJwtSigner rsaJwtSigner;
    private final RsaJwtValidator rsaJwtValidator;
    private final Clock clock;
    private final Optional<AccountLockoutPort> accountLockoutPort;
    private final Optional<AccountReadyEventPublisherPort> accountReadyEventPublisherPort;
    private final AuthSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    // =================================================================================================================
    // Use Cases (Inbound Ports)
    // =================================================================================================================

    @Override
    public Mono<LoginResult> login(LoginCommand command) {
        if (command == null) {
            return Mono.error(new NullPointerException("command must not be null"));
        }

        String sanitizedCpf = sanitizeCpf(command.cpf());
        if (sanitizedCpf.length() != 11) {
            return timingDefenseRejection(command.password(), "CPF is not 11 digits");
        }

        Instant now = Instant.now(clock);

        return checkRedisLockout(sanitizedCpf)
                .then(Mono.defer(() -> findUserOrDefend(sanitizedCpf, command.password())))
                .flatMap(user -> checkUserLockout(user, now))
                .flatMap(user -> verifyPassword(command.password(), user.getPasswordHash())
                        .flatMap(matches -> Boolean.TRUE.equals(matches)
                                ? handleSuccessfulLogin(user, sanitizedCpf, now)
                                : handleFailedLogin(user, sanitizedCpf, now)));
    }

    @Override
    public Mono<LoginResult> refreshToken(RefreshTokenCommand command) {
        if (command == null) {
            return Mono.error(new NullPointerException("command must not be null"));
        }
        if (command.refreshToken() == null || command.refreshToken().isBlank()) {
            return Mono.error(new InvalidCredentialsException(REFRESH_TOKEN_INVALID_MSG));
        }

        Instant now = Instant.now(clock);

        return authUserRepositoryPort.findByRefreshToken(command.refreshToken().trim())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Refresh token not found or invalid");
                    return Mono.error(new InvalidCredentialsException(REFRESH_TOKEN_INVALID_MSG));
                }))
                .flatMap(user -> checkUserLockout(user, now))
                .flatMap(user -> validateRefreshTokenNotExpired(user, now))
                .flatMap(user -> issueTokens(user, now));
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

        Instant now = Instant.now(clock);

        return checkCpfNotRegistered(sanitizedCpf)
                .then(Mono.defer(() -> encodePassword(command.password())))
                .map(hash -> buildNewAuthUser(command, sanitizedCpf, hash, now))
                .flatMap(authUserRepositoryPort::save)
                .doOnSuccess(saved -> log.info("Credentials registered successfully for customerId: {}", saved.getCustomerId()))
                .flatMap(saved -> publishAccountReadyEvent(saved, now));
    }

    @Override
    public Mono<IntrospectResult> introspect(IntrospectCommand command) {
        if (command == null || command.token() == null || command.token().isBlank()) {
            return Mono.just(IntrospectResult.inactive());
        }

        return Mono.fromCallable(() -> buildIntrospectResult(command.token()))
                .onErrorResume(e -> {
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

    // =================================================================================================================
    // Helper Methods: Validação, Lockout e Defesa contra Timing Attack
    // =================================================================================================================

    private String sanitizeCpf(String cpf) {
        return cpf != null ? cpf.replaceAll("\\D", "") : "";
    }

    private <T> Mono<T> timingDefenseRejection(String rawPassword, String logReason) {
        String pwd = rawPassword != null ? rawPassword : "";
        return Mono.fromCallable(() -> passwordEncoderPort.matches(pwd, properties.getEffectiveDummyHash()))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> {
                    log.warn("Login rejected: {}", logReason);
                    return Mono.error(new InvalidCredentialsException(INVALID_CREDENTIALS_MSG));
                }));
    }

    private Mono<Void> checkRedisLockout(String cpf) {
        return accountLockoutPort
                .map(port -> port.isLocked(cpf))
                .orElseGet(() -> Mono.just(false))
                .flatMap(locked -> Boolean.TRUE.equals(locked)
                        ? Mono.error(new CustomerLockedException("Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em 15 minutos."))
                        : Mono.empty());
    }

    private Mono<AuthUser> findUserOrDefend(String sanitizedCpf, String password) {
        return authUserRepositoryPort.findByCpf(sanitizedCpf)
                .switchIfEmpty(Mono.defer(() -> timingDefenseRejection(password, "user not found for CPF")));
    }

    private Mono<AuthUser> checkUserLockout(AuthUser user, Instant now) {
        if (user.isLocked(now)) {
            long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
            log.warn("Rejected: account locked for customerId: {}, minutes left: {}", user.getCustomerId(), minutesLeft);
            return Mono.error(new CustomerLockedException(
                    "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
            ));
        }
        return Mono.just(user);
    }

    private long calculateMinutesLeft(Instant lockedUntil, Instant now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1);
    }

    private Mono<AuthUser> validateRefreshTokenNotExpired(AuthUser user, Instant now) {
        if (user.getRefreshTokenExpiresAt() == null || !user.getRefreshTokenExpiresAt().isAfter(now)) {
            log.warn("Refresh rejected: refresh token expired for customerId: {}", user.getCustomerId());
            return Mono.error(new InvalidCredentialsException(REFRESH_TOKEN_EXPIRED_MSG));
        }
        return Mono.just(user);
    }

    // =================================================================================================================
    // Helper Methods: Senhas e Criptografia (Virtual Threads / boundedElastic)
    // =================================================================================================================

    private Mono<Boolean> verifyPassword(String rawPassword, String passwordHash) {
        String pwd = rawPassword != null ? rawPassword : "";
        String hash = passwordHash != null ? passwordHash : "";
        return Mono.fromCallable(() -> passwordEncoderPort.matches(pwd, hash))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<String> encodePassword(String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoderPort.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // =================================================================================================================
    // Helper Methods: Sucesso e Falha de Login
    // =================================================================================================================

    private Mono<LoginResult> handleSuccessfulLogin(AuthUser user, String sanitizedCpf, Instant now) {
        Mono<Void> resetRedisMono = accountLockoutPort
                .map(port -> port.resetFailedAttempts(sanitizedCpf))
                .orElseGet(Mono::empty);

        return resetRedisMono.then(Mono.defer(() -> {
            user.resetFailedLogins(now);

            if (user.hasActiveToken(now)) {
                long remainingSeconds = Duration.between(now, user.getTokenExpiresAt()).getSeconds();
                if (remainingSeconds > 0) {
                    return reuseExistingToken(user, remainingSeconds);
                }
            }

            return issueTokens(user, now);
        }));
    }

    private Mono<LoginResult> handleFailedLogin(AuthUser user, String sanitizedCpf, Instant now) {
        user.recordFailedLogin(now);

        return authUserRepositoryPort.save(user)
                .then(Mono.defer(() -> recordFailedAttempt(sanitizedCpf)))
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
                    return Mono.error(new InvalidCredentialsException(INVALID_CREDENTIALS_MSG));
                });
    }

    private Mono<Long> recordFailedAttempt(String cpf) {
        return accountLockoutPort
                .map(port -> port.recordFailedAttempt(cpf))
                .orElseGet(() -> Mono.just(0L));
    }

    // =================================================================================================================
    // Helper Methods: Emissão, Reuso e Transformação de Tokens
    // =================================================================================================================

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Mono<LoginResult> reuseExistingToken(AuthUser user, long remainingSeconds) {
        return authUserRepositoryPort.save(user)
                .map(saved -> {
                    log.info("Active token reused for customerId: {} with remaining TTL: {}s", saved.getCustomerId(), remainingSeconds);
                    return toLoginResult(saved, saved.getActiveToken(), remainingSeconds, saved.getRefreshToken());
                });
    }

    private Mono<LoginResult> issueTokens(AuthUser user, Instant now) {
        TokenClaims claims = createTokenClaims(user);
        String token = rsaJwtSigner.generateToken(claims);
        String refreshToken = generateSecureToken();
        Instant tokenExpiresAt = now.plusSeconds(properties.getAccessTokenValiditySeconds());
        Instant refreshTokenExpiresAt = now.plusSeconds(properties.getRefreshTokenValiditySeconds());

        user.updateTokens(token, tokenExpiresAt, refreshToken, refreshTokenExpiresAt, now);
        return authUserRepositoryPort.save(user)
                .map(saved -> {
                    log.info("Tokens generated and saved for customerId: {}", saved.getCustomerId());
                    return toLoginResult(saved, token, properties.getAccessTokenValiditySeconds(), refreshToken);
                });
    }

    private TokenClaims createTokenClaims(AuthUser user) {
        return new TokenClaims(
                user.getCustomerId(),
                user.getAccountId(),
                user.getCpf(),
                user.getRole(),
                user.getBranch(),
                user.getAccountNumber()
        );
    }

    private LoginResult toLoginResult(AuthUser user, String token, long expiresIn, String refreshToken) {
        return new LoginResult(
                token,
                expiresIn,
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

    private IntrospectResult buildIntrospectResult(String token) {
        TokenClaims claims = rsaJwtValidator.validate(token);
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
    }

    // =================================================================================================================
    // Helper Methods: Registro e Publicação de Eventos
    // =================================================================================================================

    private Mono<Void> checkCpfNotRegistered(String cpf) {
        return authUserRepositoryPort.existsByCpf(cpf)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.error(new CredentialsAlreadyExistsException("Credenciais já cadastradas para o CPF: " + cpf))
                        : Mono.empty());
    }

    private AuthUser buildNewAuthUser(RegisterCredentialsCommand command, String sanitizedCpf, String hashedPassword, Instant now) {
        return new AuthUser(
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
    }

    private Mono<AuthUser> publishAccountReadyEvent(AuthUser saved, Instant now) {
        if (accountReadyEventPublisherPort.isEmpty()) {
            return Mono.just(saved);
        }
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
}
