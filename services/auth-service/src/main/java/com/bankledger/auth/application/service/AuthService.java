package com.bankledger.auth.application.service;

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
import com.bankledger.auth.application.port.out.AuthUserRepositoryPort;
import com.bankledger.auth.application.port.out.PasswordEncoderPort;
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.auth.domain.exception.CustomerLockedException;
import com.bankledger.auth.domain.exception.InvalidCredentialsException;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
import com.bankledger.commons.security.SecurityConstants;
import com.bankledger.commons.security.TokenClaims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class AuthService implements LoginUseCase, RefreshTokenUseCase, RegisterCredentialsUseCase, IntrospectTokenUseCase {

    private final AuthUserRepositoryPort authUserRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final RsaJwtSigner rsaJwtSigner;
    private final RsaJwtValidator rsaJwtValidator;
    private final Clock clock;
    private final String dummyArgon2Hash;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AuthUserRepositoryPort authUserRepositoryPort,
            PasswordEncoderPort passwordEncoderPort,
            RsaJwtSigner rsaJwtSigner,
            RsaJwtValidator rsaJwtValidator,
            Clock clock,
            @Value("${app.security.dummy-argon2-hash:${APP_SECURITY_DUMMY_ARGON2_HASH:$argon2id$v=19$m=19456,t=3,p=1$A95dCHZGbTCnwE0PsaX0Vw$PpVtTpb8S+tIiPPH7dFQLQqb1quYB17eMUJZI+Mbcz8}}")
            String dummyArgon2Hash,
            @Value("${app.security.access-token-validity-seconds:${ACCESS_TOKEN_VALIDITY_SECONDS:900}}")
            long accessTokenValiditySeconds,
            @Value("${app.security.refresh-token-validity-seconds:${REFRESH_TOKEN_VALIDITY_SECONDS:604800}}")
            long refreshTokenValiditySeconds
    ) {
        this.authUserRepositoryPort = Objects.requireNonNull(authUserRepositoryPort, "authUserRepositoryPort must not be null");
        this.passwordEncoderPort = Objects.requireNonNull(passwordEncoderPort, "passwordEncoderPort must not be null");
        this.rsaJwtSigner = Objects.requireNonNull(rsaJwtSigner, "rsaJwtSigner must not be null");
        this.rsaJwtValidator = Objects.requireNonNull(rsaJwtValidator, "rsaJwtValidator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.dummyArgon2Hash = dummyArgon2Hash;
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

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
            passwordEncoderPort.matches(command.password() != null ? command.password() : "", dummyArgon2Hash);
            log.warn("Login rejected: CPF is not 11 digits");
            throw new InvalidCredentialsException("Credenciais inválidas");
        }

        Instant now = Instant.now(clock);

        Optional<AuthUser> maybeUser = authUserRepositoryPort.findByCpf(sanitizedCpf);
        if (maybeUser.isEmpty()) {
            // Timing side-channel defense: simulate Argon2id password hash check to prevent CPF enumeration
            passwordEncoderPort.matches(command.password() != null ? command.password() : "", dummyArgon2Hash);
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

            if (user.isLocked(now)) {
                long minutesLeft = calculateMinutesLeft(user.getLockedUntil(), now);
                log.warn("Account newly locked after 5 failed attempts for customerId: {}", user.getCustomerId());
                throw new CustomerLockedException(
                        "Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em " + minutesLeft + " minutos."
                );
            }

            log.warn("Login rejected: invalid password for customerId: {}. Failed attempts: {}",
                    user.getCustomerId(), user.getFailedLoginAttempts());
            throw new InvalidCredentialsException("Credenciais inválidas");
        }

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
        Instant tokenExpiresAt = now.plusSeconds(accessTokenValiditySeconds);
        Instant refreshTokenExpiresAt = now.plusSeconds(refreshTokenValiditySeconds);

        user.updateTokens(token, tokenExpiresAt, refreshToken, refreshTokenExpiresAt, now);
        authUserRepositoryPort.save(user);
        log.info("Fresh tokens generated and issued for customerId: {}", user.getCustomerId());

        return new LoginResult(
                token,
                accessTokenValiditySeconds,
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
        Instant tokenExpiresAt = now.plusSeconds(accessTokenValiditySeconds);
        Instant refreshTokenExpiresAt = now.plusSeconds(refreshTokenValiditySeconds);

        user.updateTokens(newAccessToken, tokenExpiresAt, newRefreshToken, refreshTokenExpiresAt, now);
        authUserRepositoryPort.save(user);
        log.info("Token rotated and refreshed for customerId: {}", user.getCustomerId());

        return new LoginResult(
                newAccessToken,
                accessTokenValiditySeconds,
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

    private long calculateMinutesLeft(Instant lockedUntil, Instant now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, lockedUntil).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }
}
