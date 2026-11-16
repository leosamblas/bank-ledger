package com.bankledger.auth;

import com.bankledger.auth.adapters.in.rest.AuthController;
import com.bankledger.auth.adapters.in.rest.GlobalExceptionHandler;
import com.bankledger.auth.adapters.in.rest.InternalAuthController;
import com.bankledger.auth.adapters.in.rest.dto.IntrospectRequest;
import com.bankledger.auth.adapters.in.rest.dto.LoginRequest;
import com.bankledger.auth.adapters.in.rest.dto.RegisterCredentialsRequest;
import com.bankledger.auth.application.port.in.IntrospectCommand;
import com.bankledger.auth.application.port.in.IntrospectResult;
import com.bankledger.auth.application.port.in.IntrospectTokenUseCase;
import com.bankledger.auth.application.port.in.LoginCommand;
import com.bankledger.auth.application.port.in.LoginResult;
import com.bankledger.auth.application.port.in.LoginUseCase;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.auth.domain.exception.CredentialsAlreadyExistsException;
import com.bankledger.auth.domain.exception.CustomerLockedException;
import com.bankledger.auth.domain.exception.InvalidCredentialsException;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.security.RsaJwtSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private MockMvc internalMockMvc;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private LoginUseCase loginUseCase;

    @Mock
    private IntrospectTokenUseCase introspectTokenUseCase;

    @Mock
    private RegisterCredentialsUseCase registerCredentialsUseCase;

    private RsaJwtSigner rsaJwtSigner;

    @BeforeEach
    void setUp() {
        rsaJwtSigner = new RsaJwtSigner(objectMapper);

        AuthController authController = new AuthController(loginUseCase, introspectTokenUseCase, rsaJwtSigner);
        InternalAuthController internalAuthController = new InternalAuthController(registerCredentialsUseCase);
        GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(globalExceptionHandler)
                .setMessageConverters(converter)
                .build();

        internalMockMvc = MockMvcBuilders
                .standaloneSetup(internalAuthController)
                .setControllerAdvice(globalExceptionHandler)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("200 OK with token and user details when credentials are valid")
    void shouldReturn200WithTokenWhenCredentialsValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        LoginResult loginResult = new LoginResult(
                "mocked.rs256.jwt.token",
                3600L,
                "Bearer",
                new LoginResult.UserInfo(customerId, "Carlos Eduardo Silva", "52998224725", "carlos@example.com")
        );
        when(loginUseCase.login(any(LoginCommand.class))).thenReturn(loginResult);

        LoginRequest request = new LoginRequest("52998224725", "Secret@123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked.rs256.jwt.token"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.id").value(customerId.toString()))
                .andExpect(jsonPath("$.user.fullName").value("Carlos Eduardo Silva"))
                .andExpect(jsonPath("$.user.cpf").value("52998224725"))
                .andExpect(jsonPath("$.user.email").value("carlos@example.com"));
    }

    @Test
    @DisplayName("200 OK on alternate /auth/login path")
    void shouldReturn200OnAlternateLoginPath() throws Exception {
        UUID customerId = UUID.randomUUID();
        LoginResult loginResult = new LoginResult(
                "mocked.token",
                3600L,
                "Bearer",
                new LoginResult.UserInfo(customerId, "Carlos Silva", "52998224725", "carlos@example.com")
        );
        when(loginUseCase.login(any(LoginCommand.class))).thenReturn(loginResult);

        LoginRequest request = new LoginRequest("52998224725", "Secret@123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked.token"));
    }

    @Test
    @DisplayName("401 Unauthorized with ProblemDetail when credentials are invalid")
    void shouldReturn401WhenInvalidCredentials() throws Exception {
        when(loginUseCase.login(any(LoginCommand.class)))
                .thenThrow(new InvalidCredentialsException("Credenciais inválidas"));

        LoginRequest request = new LoginRequest("52998224725", "WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value(containsString("Credenciais inválidas")));
    }

    @Test
    @DisplayName("401 Unauthorized with ProblemDetail when customer is locked")
    void shouldReturn401WhenCustomerLocked() throws Exception {
        when(loginUseCase.login(any(LoginCommand.class)))
                .thenThrow(new CustomerLockedException("Conta bloqueada temporariamente por excesso de tentativas. Tente novamente em 15 minutos."));

        LoginRequest request = new LoginRequest("52998224725", "Secret@123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(containsString("bloqueada temporariamente")));
    }

    @Test
    @DisplayName("400 Bad Request when required fields are missing on login")
    void shouldReturn400WhenMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("200 OK with valid RFC 7517 JWKS on /.well-known/jwks.json")
    void shouldReturnJwksOnWellKnownEndpoint() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").isNotEmpty());
    }

    @Test
    @DisplayName("200 OK with valid RFC 7517 JWKS on /api/v1/auth/.well-known/jwks.json")
    void shouldReturnJwksOnApiPrefixEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/auth/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    @DisplayName("200 OK with active=true when token is introspected successfully")
    void shouldReturnIntrospectionResultWhenTokenValid() throws Exception {
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

        mockMvc.perform(post("/api/v1/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sub").value("52998224725"))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("200 OK with active=false when token is invalid during introspection")
    void shouldReturnInactiveWhenTokenInvalid() throws Exception {
        when(introspectTokenUseCase.introspect(any(IntrospectCommand.class)))
                .thenReturn(IntrospectResult.inactive());

        IntrospectRequest request = new IntrospectRequest("invalid.jwt.token");

        mockMvc.perform(post("/api/v1/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("201 Created when registering credentials via internal endpoint")
    void shouldReturn201WhenRegisteringCredentials() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$argon2id$mockhash",
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

        internalMockMvc.perform(post("/internal/v1/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(authUser.getId().toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.cpf").value("52998224725"));
    }

    @Test
    @DisplayName("409 Conflict when registering credentials with duplicate CPF")
    void shouldReturn409WhenDuplicateCpf() throws Exception {
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

        internalMockMvc.perform(post("/internal/v1/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(containsString("já cadastradas")));
    }
}
