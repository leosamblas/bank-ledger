package com.bankledger.auth;

import com.bankledger.auth.adapters.in.rest.AuthController;
import com.bankledger.auth.adapters.in.rest.GlobalExceptionHandler;
import com.bankledger.auth.adapters.in.rest.InternalAuthController;
import com.bankledger.auth.adapters.in.rest.dto.IntrospectRequest;
import com.bankledger.auth.adapters.in.rest.dto.LoginRequest;
import com.bankledger.auth.adapters.in.rest.dto.RefreshTokenRequest;
import com.bankledger.auth.adapters.in.rest.dto.RegisterCredentialsRequest;
import com.bankledger.auth.config.AuthSecurityProperties;
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
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.auth.domain.exception.CustomerLockedException;
import com.bankledger.auth.domain.exception.InvalidCredentialsException;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.SecurityConstants;
import com.bankledger.commons.security.dto.RegisterCredentialsPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private WebTestClient client;
    private WebTestClient internalClient;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private LoginUseCase loginUseCase;

    @Mock
    private RefreshTokenUseCase refreshTokenUseCase;

    @Mock
    private IntrospectTokenUseCase introspectTokenUseCase;

    @Mock
    private RegisterCredentialsUseCase registerCredentialsUseCase;

    private RsaJwtSigner rsaJwtSigner;

    @BeforeEach
    void setUp() {
        rsaJwtSigner = new RsaJwtSigner(objectMapper);

        AuthController authController = new AuthController(loginUseCase, refreshTokenUseCase, introspectTokenUseCase, rsaJwtSigner);
        InternalAuthController internalAuthController = new InternalAuthController(
                registerCredentialsUseCase,
                Optional.empty(),
                Optional.empty(),
                Clock.systemUTC(),
                new AuthSecurityProperties()
        );
        GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

        client = WebTestClient.bindToController(authController)
                .controllerAdvice(globalExceptionHandler)
                .build();

        internalClient = WebTestClient.bindToController(internalAuthController)
                .controllerAdvice(globalExceptionHandler)
                .build();
    }

    @Test
    @DisplayName("200 OK with token and user details when credentials are valid")
    void shouldReturn200WithTokenWhenCredentialsValid() {
        UUID customerId = UUID.randomUUID();
        LoginResult loginResult = new LoginResult(
                "mocked.rs256.jwt.token",
                900L,
                "Bearer",
                "mocked.refresh.token.hex",
                new LoginResult.UserInfo(customerId, "Carlos Eduardo Silva", "52998224725", "carlos@example.com")
        );
        when(loginUseCase.login(any(LoginCommand.class))).thenReturn(loginResult);

        LoginRequest request = new LoginRequest("52998224725", "Secret@123");

        client.post()
                .uri(SecurityConstants.AUTH_LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mocked.rs256.jwt.token")
                .jsonPath("$.expiresIn").isEqualTo(900)
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.refreshToken").isEqualTo("mocked.refresh.token.hex")
                .jsonPath("$.user.id").isEqualTo(customerId.toString())
                .jsonPath("$.user.fullName").isEqualTo("Carlos Eduardo Silva")
                .jsonPath("$.user.cpf").isEqualTo("52998224725")
                .jsonPath("$.user.email").isEqualTo("carlos@example.com");
    }

    @Test
    @DisplayName("404 Not Found on unversioned /auth/login path")
    void shouldReturn404OnUnversionedLoginPath() {
        LoginRequest request = new LoginRequest("52998224725", "Secret@123");

        client.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("200 OK when refreshing session with valid refresh token")
    void shouldReturn200OnRefreshToken() {
        UUID customerId = UUID.randomUUID();
        LoginResult refreshed = new LoginResult(
                "fresh.rs256.jwt.token",
                900L,
                "Bearer",
                "rotated.refresh.token.hex",
                new LoginResult.UserInfo(customerId, "Carlos Silva", "52998224725", "carlos@example.com")
        );
        when(refreshTokenUseCase.refreshToken(any(RefreshTokenCommand.class))).thenReturn(refreshed);

        RefreshTokenRequest request = new RefreshTokenRequest("current.refresh.token");

        client.post()
                .uri(SecurityConstants.AUTH_REFRESH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("fresh.rs256.jwt.token")
                .jsonPath("$.refreshToken").isEqualTo("rotated.refresh.token.hex")
                .jsonPath("$.expiresIn").isEqualTo(900);
    }

    @Test
    @DisplayName("401 Unauthorized with ProblemDetail when credentials are invalid")
    void shouldReturn401WhenInvalidCredentials() {
        when(loginUseCase.login(any(LoginCommand.class)))
                .thenThrow(new InvalidCredentialsException("Credenciais inválidas"));

        LoginRequest request = new LoginRequest("52998224725", "WrongPassword");

        client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Unauthorized")
                .jsonPath("$.detail").value(detail -> org.assertj.core.api.Assertions.assertThat(detail.toString()).contains("Credenciais inválidas"));
    }

    @Test
    @DisplayName("401 Unauthorized with ProblemDetail when customer is locked")
    void shouldReturn401WhenCustomerLocked() {
        when(loginUseCase.login(any(LoginCommand.class)))
                .thenThrow(new CustomerLockedException("Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em 15 minutos."));

        LoginRequest request = new LoginRequest("52998224725", "Secret@123");

        client.post()
                .uri(SecurityConstants.AUTH_LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").value(detail -> org.assertj.core.api.Assertions.assertThat(detail.toString()).contains("bloqueada temporariamente"));
    }

    @Test
    @DisplayName("400 Bad Request when required fields are missing on login")
    void shouldReturn400WhenMissingFields() {
        client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }

    @Test
    @DisplayName("200 OK with valid RFC 7517 JWKS on /.well-known/jwks.json")
    void shouldReturnJwksOnWellKnownEndpoint() {
        client.get()
                .uri(SecurityConstants.JWKS_WELL_KNOWN_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.keys").isArray()
                .jsonPath("$.keys[0].kty").isEqualTo("RSA")
                .jsonPath("$.keys[0].use").isEqualTo("sig")
                .jsonPath("$.keys[0].alg").isEqualTo("RS256")
                .jsonPath("$.keys[0].kid").isNotEmpty()
                .jsonPath("$.keys[0].n").isNotEmpty()
                .jsonPath("$.keys[0].e").isNotEmpty();
    }

    @Test
    @DisplayName("404 Not Found on removed unversioned /api/v1/auth/.well-known/jwks.json path")
    void shouldReturn404OnUnversionedJwksPath() {
        client.get()
                .uri("/api/v1/auth/.well-known/jwks.json")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("200 OK with active=true when token is introspected successfully")
    void shouldReturnIntrospectionResultWhenTokenValid() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        IntrospectResult result = new IntrospectResult(
                true,
                "52998224725",
                customerId,
                accountId,
                "0001",
                "123456",
                "ROLE_USER",
                3600L,
                1000L,
                "https://auth.bank.internal"
        );
        when(introspectTokenUseCase.introspect(any(IntrospectCommand.class))).thenReturn(result);

        IntrospectRequest request = new IntrospectRequest("valid.rs256.jwt");

        client.post()
                .uri(SecurityConstants.AUTH_INTROSPECT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.sub").isEqualTo("52998224725")
                .jsonPath("$.customerId").isEqualTo(customerId.toString())
                .jsonPath("$.accountId").isEqualTo(accountId.toString())
                .jsonPath("$.role").isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("200 OK with active=false when token is invalid during introspection")
    void shouldReturnInactiveWhenTokenInvalid() {
        when(introspectTokenUseCase.introspect(any(IntrospectCommand.class)))
                .thenReturn(IntrospectResult.inactive());

        IntrospectRequest request = new IntrospectRequest("invalid.jwt.token");

        client.post()
                .uri("/api/v1/auth/introspect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(false);
    }

    @Test
    @DisplayName("201 Created when registering credentials via internal endpoint")
    void shouldReturn201WhenRegisteringCredentials() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$2a$10$mockedbcryptpasswordhash",
                "ROLE_USER",
                customerId,
                accountId,
                "0001",
                "123456",
                0,
                null,
                "Carlos Silva",
                "carlos@example.com",
                Instant.now(),
                Instant.now()
        );

        when(registerCredentialsUseCase.registerCredentials(any(RegisterCredentialsCommand.class)))
                .thenReturn(authUser);

        RegisterCredentialsRequest request = new RegisterCredentialsRequest(
                customerId,
                "52998224725",
                "Secret@123",
                accountId,
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        );

        internalClient.post()
                .uri(SecurityConstants.INTERNAL_AUTH_CREDENTIALS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(authUser.getId().toString())
                .jsonPath("$.customerId").isEqualTo(customerId.toString())
                .jsonPath("$.cpf").isEqualTo("52998224725");
    }

    @Test
    @DisplayName("201 Created when registering credentials mapped from RegisterCredentialsPayload")
    void shouldReturn201WhenRegisteringFromPayload() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$2a$10$mockedbcryptpasswordhash",
                "ROLE_USER",
                customerId,
                accountId,
                "0001",
                "123456",
                0,
                null,
                "Carlos Silva",
                "carlos@example.com",
                Instant.now(),
                Instant.now()
        );

        when(registerCredentialsUseCase.registerCredentials(any(RegisterCredentialsCommand.class)))
                .thenReturn(authUser);

        RegisterCredentialsPayload payload = new RegisterCredentialsPayload(
                customerId,
                "52998224725",
                "Secret@123",
                accountId,
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        );
        RegisterCredentialsRequest request = RegisterCredentialsRequest.fromPayload(payload);

        internalClient.post()
                .uri(SecurityConstants.INTERNAL_AUTH_CREDENTIALS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(authUser.getId().toString())
                .jsonPath("$.customerId").isEqualTo(customerId.toString())
                .jsonPath("$.cpf").isEqualTo("52998224725");
    }

    @Test
    @DisplayName("409 Conflict when registering credentials with duplicate CPF")
    void shouldReturn409WhenDuplicateCpf() {
        when(registerCredentialsUseCase.registerCredentials(any(RegisterCredentialsCommand.class)))
                .thenThrow(new CredentialsAlreadyExistsException("Credenciais já cadastradas para o CPF: 52998224725"));

        RegisterCredentialsRequest request = new RegisterCredentialsRequest(
                UUID.randomUUID(),
                "52998224725",
                "Secret@123",
                UUID.randomUUID(),
                "0001",
                "123456",
                "Carlos Silva",
                "carlos@example.com"
        );

        internalClient.post()
                .uri(SecurityConstants.INTERNAL_AUTH_CREDENTIALS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").value(detail -> org.assertj.core.api.Assertions.assertThat(detail.toString()).contains("já cadastradas"));
    }
}
