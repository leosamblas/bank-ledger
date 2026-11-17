package com.bankledger.auth;

import com.bankledger.auth.application.port.in.IntrospectCommand;
import com.bankledger.auth.application.port.in.IntrospectResult;
import com.bankledger.auth.application.port.in.LoginCommand;
import com.bankledger.auth.application.port.in.LoginResult;
import com.bankledger.auth.application.port.in.RefreshTokenCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.out.AccountLockoutPort;
import com.bankledger.auth.application.port.out.AccountReadyEventPublisherPort;
import com.bankledger.auth.application.port.out.AuthUserRepositoryPort;
import com.bankledger.auth.application.port.out.PasswordEncoderPort;
import com.bankledger.auth.application.service.AuthService;
import com.bankledger.auth.config.AuthSecurityProperties;
import com.bankledger.auth.domain.event.AccountReadyEvent;
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.auth.domain.exception.CustomerLockedException;
import com.bankledger.auth.domain.exception.InvalidCredentialsException;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
import com.bankledger.commons.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-16T12:00:00Z");
    private static final String DUMMY_HASH = "$argon2id$v=19$m=19456,t=3,p=1$mockdummyhash";

    @Mock
    private AuthUserRepositoryPort authUserRepositoryPort;

    @Mock
    private PasswordEncoderPort passwordEncoderPort;

    @Mock
    private AccountLockoutPort accountLockoutPort;

    @Mock
    private AccountReadyEventPublisherPort accountReadyEventPublisherPort;

    private RsaJwtSigner rsaJwtSigner;
    private RsaJwtValidator rsaJwtValidator;
    private Clock clock;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(T0, ZoneOffset.UTC);
        JsonMapper mapper = JsonMapper.builder().build();
        rsaJwtSigner = new RsaJwtSigner(mapper, clock, "test-kid");

        JwksPublicKeyProvider keyProvider = new JwksPublicKeyProvider(mapper);
        keyProvider.putKey(rsaJwtSigner.getKeyId(), rsaJwtSigner.getPublicKey());
        rsaJwtValidator = new RsaJwtValidator(keyProvider, mapper, clock);

        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setDummyHash(DUMMY_HASH);
        properties.setAccessTokenValiditySeconds(900L);
        properties.setRefreshTokenValiditySeconds(604800L);

        authService = new AuthService(
                authUserRepositoryPort,
                passwordEncoderPort,
                rsaJwtSigner,
                rsaJwtValidator,
                clock,
                Optional.ofNullable(accountLockoutPort),
                Optional.ofNullable(accountReadyEventPublisherPort),
                properties
        );
    }

    private AuthUser buildUser(int failedAttempts, Instant lockedUntil) {
        return new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$argon2id$mockedhash",
                SecurityConstants.ROLE_USER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "0001",
                "123456",
                failedAttempts,
                lockedUntil,
                "Carlos Silva",
                "carlos@example.com",
                null,
                null,
                null,
                null,
                T0,
                T0
        );
    }

    @Test
    @DisplayName("Should successfully login, mint token and refresh token when user has no active token")
    void shouldLoginSuccessfully() {
        AuthUser user = buildUser(0, null);
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("Secret@123", "$argon2id$mockedhash")).thenReturn(true);
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login(new LoginCommand("52998224725", "Secret@123"));

        assertThat(result.token()).isNotEmpty();
        assertThat(result.expiresIn()).isEqualTo(900L);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.refreshToken()).isNotEmpty();
        assertThat(result.user().cpf()).isEqualTo("52998224725");

        var claims = rsaJwtValidator.validate(result.token());
        assertThat(claims.cpf()).isEqualTo("52998224725");
        assertThat(claims.branch()).isEqualTo("0001");
        assertThat(claims.accountNumber()).isEqualTo("123456");
    }

    @Test
    @DisplayName("Should reuse active token and compute dynamic remaining TTL on repeated login")
    void shouldReuseActiveTokenWithDynamicRemainingTtl() {
        AuthUser user = buildUser(0, null);
        String existingToken = "existing.active.jwt.token";
        String existingRefreshToken = "existing.refresh.token.hex";
        Instant tokenExpiresAt = T0.plusSeconds(420); // 420 seconds remaining
        Instant refreshExpiresAt = T0.plusSeconds(86400);

        user.updateTokens(existingToken, tokenExpiresAt, existingRefreshToken, refreshExpiresAt, T0);

        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("Secret@123", "$argon2id$mockedhash")).thenReturn(true);
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login(new LoginCommand("52998224725", "Secret@123"));

        // Must return the exact same token with remaining TTL
        assertThat(result.token()).isEqualTo(existingToken);
        assertThat(result.expiresIn()).isEqualTo(420L);
        assertThat(result.refreshToken()).isEqualTo(existingRefreshToken);
    }

    @Test
    @DisplayName("Should rotate access token and refresh token upon valid refresh request")
    void shouldRotateTokensOnValidRefreshToken() {
        AuthUser user = buildUser(0, null);
        String initialRefreshToken = "valid.initial.refresh.token";
        user.updateTokens("old.token", T0.minusSeconds(10), initialRefreshToken, T0.plusSeconds(3600), T0);

        when(authUserRepositoryPort.findByRefreshToken(initialRefreshToken)).thenReturn(Optional.of(user));
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.refreshToken(new RefreshTokenCommand(initialRefreshToken));

        assertThat(result.token()).isNotEmpty().isNotEqualTo("old.token");
        assertThat(result.expiresIn()).isEqualTo(900L);
        assertThat(result.refreshToken()).isNotEmpty().isNotEqualTo(initialRefreshToken);
    }

    @Test
    @DisplayName("Should reject refresh token when token has expired")
    void shouldRejectExpiredRefreshToken() {
        AuthUser user = buildUser(0, null);
        String expiredRefreshToken = "expired.refresh.token";
        user.updateTokens("old.token", T0.minusSeconds(100), expiredRefreshToken, T0.minusSeconds(1), T0);

        when(authUserRepositoryPort.findByRefreshToken(expiredRefreshToken)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenCommand(expiredRefreshToken)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    @DisplayName("Should reject refresh token when token does not exist")
    void shouldRejectNonexistentRefreshToken() {
        when(authUserRepositoryPort.findByRefreshToken("unknown.token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenCommand("unknown.token")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Should sanitize formatted CPF during login (e.g. 529.982.247-25)")
    void shouldSanitizeFormattedCpfDuringLogin() {
        AuthUser user = buildUser(0, null);
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("Secret@123", "$argon2id$mockedhash")).thenReturn(true);
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login(new LoginCommand("529.982.247-25", "Secret@123"));

        assertThat(result.token()).isNotEmpty();
        verify(authUserRepositoryPort).findByCpf("52998224725");
    }

    @Test
    @DisplayName("Should execute dummy password check to defend against timing side-channel when user not found")
    void shouldPerformDummyHashCheckWhenUserNotFound() {
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("52998224725", "Secret@123")))
                .isInstanceOf(InvalidCredentialsException.class);

        // Verify injected dummy hash was used
        verify(passwordEncoderPort).matches("Secret@123", DUMMY_HASH);
    }

    @Test
    @DisplayName("Should execute dummy password check on malformed CPF length")
    void shouldPerformDummyHashCheckOnMalformedCpf() {
        assertThatThrownBy(() -> authService.login(new LoginCommand("12345", "Secret@123")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoderPort).matches("Secret@123", DUMMY_HASH);
    }

    @Test
    @DisplayName("Should increment failed attempts on wrong password")
    void shouldIncrementFailedAttemptsOnWrongPassword() {
        AuthUser user = buildUser(0, null);
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("WrongPass", "$argon2id$mockedhash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("52998224725", "WrongPass")))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepositoryPort).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should lock customer on 5th consecutive failed login attempt")
    void shouldLockCustomerOnFifthFailure() {
        AuthUser user = buildUser(4, null);
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("WrongPass", "$argon2id$mockedhash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("52998224725", "WrongPass")))
                .isInstanceOf(CustomerLockedException.class);

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepositoryPort).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(5);
        assertThat(captor.getValue().isLocked(T0)).isTrue();
    }

    @Test
    @DisplayName("Should reject login immediately when user is already locked")
    void shouldRejectWhenAlreadyLocked() {
        AuthUser user = buildUser(5, T0.plusSeconds(900));
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginCommand("52998224725", "Secret@123")))
                .isInstanceOf(CustomerLockedException.class);
    }

    @Test
    @DisplayName("Should register new credentials with sanitized CPF and encoded password")
    void shouldRegisterCredentialsWithSanitizedCpf() {
        when(authUserRepositoryPort.existsByCpf("52998224725")).thenReturn(false);
        when(passwordEncoderPort.encode("Secret@123")).thenReturn("$argon2id$encodedhash");
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthUser saved = authService.registerCredentials(new RegisterCredentialsCommand(
                customerId,
                "529.982.247-25",
                "Secret@123",
                accountId,
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        ));

        assertThat(saved.getCpf()).isEqualTo("52998224725");
        assertThat(saved.getPasswordHash()).isEqualTo("$argon2id$encodedhash");
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getAccountId()).isEqualTo(accountId);
    }

    @Test
    @DisplayName("Should throw exception when registering CPF with invalid length")
    void shouldThrowWhenRegisteringInvalidCpfLength() {
        assertThatThrownBy(() -> authService.registerCredentials(new RegisterCredentialsCommand(
                UUID.randomUUID(),
                "123456",
                "Secret@123",
                UUID.randomUUID(),
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        ))).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate CPF")
    void shouldThrowWhenDuplicateCpf() {
        when(authUserRepositoryPort.existsByCpf("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerCredentials(new RegisterCredentialsCommand(
                UUID.randomUUID(),
                "52998224725",
                "Secret@123",
                UUID.randomUUID(),
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        ))).isInstanceOf(CredentialsAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Should introspect valid RS256 token and return active claims")
    void shouldIntrospectValidToken() {
        AuthUser user = buildUser(0, null);
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("Secret@123", "$argon2id$mockedhash")).thenReturn(true);
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login(new LoginCommand("52998224725", "Secret@123"));

        IntrospectResult introspectResult = authService.introspect(new IntrospectCommand(result.token()));
        assertThat(introspectResult.active()).isTrue();
        assertThat(introspectResult.sub()).isEqualTo("52998224725");
        assertThat(introspectResult.role()).isEqualTo(SecurityConstants.ROLE_USER);
        assertThat(introspectResult.branch()).isEqualTo("0001");
        assertThat(introspectResult.accountNumber()).isEqualTo("123456");
    }

    @Test
    @DisplayName("Should return inactive when introspecting invalid token")
    void shouldReturnInactiveForInvalidToken() {
        IntrospectResult result = authService.introspect(new IntrospectCommand("invalid.token.here"));
        assertThat(result.active()).isFalse();
    }

    @Test
    @DisplayName("Should reject login immediately when locked in Redis BEFORE querying database")
    void shouldRejectLoginFastWhenLockedInRedisBeforeQueryingDatabase() {
        when(accountLockoutPort.isLocked("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginCommand("52998224725", "Secret@123")))
                .isInstanceOf(CustomerLockedException.class)
                .hasMessageContaining("Conta bloqueada temporariamente");

        verify(accountLockoutPort).isLocked("52998224725");
        verify(authUserRepositoryPort, never()).findByCpf(anyString());
    }

    @Test
    @DisplayName("Should lock account when Redis reports 5th failed attempt")
    void shouldLockAccountWhenRedisReportsFifthFailedAttempt() {
        AuthUser user = buildUser(0, null);
        when(accountLockoutPort.isLocked("52998224725")).thenReturn(false);
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("WrongPassword", "$argon2id$mockedhash")).thenReturn(false);
        when(accountLockoutPort.recordFailedAttempt("52998224725")).thenReturn(5L);

        assertThatThrownBy(() -> authService.login(new LoginCommand("52998224725", "WrongPassword")))
                .isInstanceOf(CustomerLockedException.class)
                .hasMessageContaining("Conta bloqueada temporariamente");

        verify(accountLockoutPort).recordFailedAttempt("52998224725");
    }

    @Test
    @DisplayName("Should reset failed attempts in Redis on successful login")
    void shouldResetFailedAttemptsInRedisOnSuccessfulLogin() {
        AuthUser user = buildUser(0, null);
        when(accountLockoutPort.isLocked("52998224725")).thenReturn(false);
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("Secret@123", "$argon2id$mockedhash")).thenReturn(true);
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.login(new LoginCommand("52998224725", "Secret@123"));

        verify(accountLockoutPort).resetFailedAttempts("52998224725");
    }

    @Test
    @DisplayName("Should publish AccountReadyEvent to Kafka on credentials registration")
    void shouldPublishAccountReadyEventOnRegistration() {
        when(authUserRepositoryPort.existsByCpf("52998224725")).thenReturn(false);
        when(passwordEncoderPort.encode("Secret@123")).thenReturn("$2a$10$encodedpassword");
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        authService.registerCredentials(new RegisterCredentialsCommand(
                customerId,
                "52998224725",
                "Secret@123",
                accountId,
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        ));

        ArgumentCaptor<AccountReadyEvent> eventCaptor = ArgumentCaptor.forClass(AccountReadyEvent.class);
        verify(accountReadyEventPublisherPort).publish(eventCaptor.capture());

        AccountReadyEvent event = eventCaptor.getValue();
        assertThat(event.payload().customerId()).isEqualTo(customerId);
        assertThat(event.payload().accountId()).isEqualTo(accountId);
        assertThat(event.payload().email()).isEqualTo("carlos@example.com");
        assertThat(event.payload().fullName()).isEqualTo("Carlos Silva");
    }

    @Test
    @DisplayName("Should find user by customerId and CPF")
    void shouldFindUserByCustomerIdAndCpf() {
        UUID customerId = UUID.randomUUID();
        AuthUser user = buildUser(0, null);

        when(authUserRepositoryPort.findByCustomerId(customerId)).thenReturn(Optional.of(user));
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));

        Optional<AuthUser> byCustomer = authService.findByCustomerId(customerId);
        Optional<AuthUser> byCpf = authService.findByCpf("529.982.247-25");

        assertThat(byCustomer).isPresent();
        assertThat(byCpf).isPresent();
    }
}
