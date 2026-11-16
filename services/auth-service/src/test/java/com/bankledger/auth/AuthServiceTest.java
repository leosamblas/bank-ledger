package com.bankledger.auth;

import com.bankledger.auth.application.port.in.IntrospectCommand;
import com.bankledger.auth.application.port.in.IntrospectResult;
import com.bankledger.auth.application.port.in.LoginCommand;
import com.bankledger.auth.application.port.in.LoginResult;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.out.AuthUserRepositoryPort;
import com.bankledger.auth.application.port.out.PasswordEncoderPort;
import com.bankledger.auth.application.service.AuthService;
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.auth.domain.exception.CustomerLockedException;
import com.bankledger.auth.domain.exception.InvalidCredentialsException;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-16T12:00:00Z");

    @Mock
    private AuthUserRepositoryPort authUserRepositoryPort;

    @Mock
    private PasswordEncoderPort passwordEncoderPort;

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

        authService = new AuthService(authUserRepositoryPort, passwordEncoderPort, rsaJwtSigner, rsaJwtValidator, clock);
    }

    private AuthUser buildUser(int failedAttempts, Instant lockedUntil) {
        return new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$argon2id$mockedhash",
                "ROLE_USER",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "0001",
                "123456",
                failedAttempts,
                lockedUntil,
                "Carlos Silva",
                "carlos@example.com",
                T0,
                T0
        );
    }

    @Test
    @DisplayName("Should successfully login and return valid RS256 token")
    void shouldLoginSuccessfully() {
        AuthUser user = buildUser(0, null);
        when(authUserRepositoryPort.findByCpf("52998224725")).thenReturn(Optional.of(user));
        when(passwordEncoderPort.matches("Secret@123", "$argon2id$mockedhash")).thenReturn(true);
        when(authUserRepositoryPort.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login(new LoginCommand("52998224725", "Secret@123"));

        assertThat(result.token()).isNotEmpty();
        assertThat(result.expiresIn()).isEqualTo(3600L);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.user().cpf()).isEqualTo("52998224725");

        var claims = rsaJwtValidator.validate(result.token());
        assertThat(claims.cpf()).isEqualTo("52998224725");
        assertThat(claims.branch()).isEqualTo("0001");
        assertThat(claims.accountNumber()).isEqualTo("123456");
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

        // Verify dummy hash match was executed
        verify(passwordEncoderPort).matches(eq("Secret@123"), any(String.class));
    }

    @Test
    @DisplayName("Should execute dummy password check on malformed CPF length")
    void shouldPerformDummyHashCheckOnMalformedCpf() {
        assertThatThrownBy(() -> authService.login(new LoginCommand("12345", "Secret@123")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoderPort).matches(eq("Secret@123"), any(String.class));
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
        assertThat(introspectResult.role()).isEqualTo("ROLE_USER");
        assertThat(introspectResult.branch()).isEqualTo("0001");
        assertThat(introspectResult.accountNumber()).isEqualTo("123456");
    }

    @Test
    @DisplayName("Should return inactive when introspecting invalid token")
    void shouldReturnInactiveForInvalidToken() {
        IntrospectResult result = authService.introspect(new IntrospectCommand("invalid.token.here"));
        assertThat(result.active()).isFalse();
    }
}
