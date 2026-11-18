package com.bankledger.account.adapters.in.rest;

import com.bankledger.account.adapters.in.rest.dto.AccountCreationRequest;
import com.bankledger.account.adapters.in.rest.dto.AccountLookupRequest;
import com.bankledger.account.adapters.in.rest.dto.AccountLookupResponse;
import com.bankledger.account.adapters.in.rest.dto.AccountResponse;
import com.bankledger.account.application.port.in.*;
import com.bankledger.account.domain.exception.*;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.commons.security.CommonAuthenticatedUser;
import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.JwtAuthenticationTokenFilter;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
import com.bankledger.commons.security.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private CreateAccountUseCase createAccountUseCase;

    @Mock
    private GetAccountMeUseCase getAccountMeUseCase;

    @Mock
    private LookupAccountUseCase lookupAccountUseCase;

    private AccountController accountController;
    private GlobalExceptionHandler globalExceptionHandler;
    private RsaJwtSigner rsaJwtSigner;
    private JwksPublicKeyProvider jwksPublicKeyProvider;
    private RsaJwtValidator rsaJwtValidator;
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    @BeforeEach
    void setUp() {
        accountController = new AccountController(createAccountUseCase, getAccountMeUseCase, lookupAccountUseCase, null);
        globalExceptionHandler = new GlobalExceptionHandler();

        rsaJwtSigner = new RsaJwtSigner();
        jwksPublicKeyProvider = new JwksPublicKeyProvider();
        jwksPublicKeyProvider.putKey(rsaJwtSigner.getKeyId(), rsaJwtSigner.getPublicKey());
        rsaJwtValidator = new RsaJwtValidator(jwksPublicKeyProvider);
        jwtAuthenticationTokenFilter = new JwtAuthenticationTokenFilter(rsaJwtValidator);

        mockMvc = MockMvcBuilders
                .standaloneSetup(accountController)
                .setControllerAdvice(globalExceptionHandler)
                .addFilters(jwtAuthenticationTokenFilter)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .build();
    }

    private UsernamePasswordAuthenticationToken createAuthToken(UUID customerId, UUID accountId) {
        return createAuthToken(customerId, accountId, "ROLE_USER");
    }

    private UsernamePasswordAuthenticationToken createAuthToken(UUID customerId, UUID accountId, String role) {
        CommonAuthenticatedUser user = new CommonAuthenticatedUser(customerId, accountId, "52998224725", role, "0001", "123456");
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Nested
    @DisplayName("POST /api/v1/accounts")
    class CreateAccountTests {

        @Test
        @DisplayName("201 Created with Location header when request data is valid")
        void shouldReturn201WithLocationHeaderWhenValid() throws Exception {
            UUID accountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            CreatedAccountResult result = new CreatedAccountResult(
                    accountId,
                    customerId,
                    "0001",
                    "123456",
                    "7",
                    AccountStatus.ACTIVE,
                    Instant.parse("2026-11-09T20:00:00Z")
            );
            when(createAccountUseCase.createAccount(any(CreateAccountCommand.class))).thenReturn(result);

            AccountCreationRequest request = new AccountCreationRequest(
                    "Carlos Eduardo Silva",
                    "52998224725",
                    LocalDate.of(1990, 5, 15),
                    "carlos@example.com",
                    "11987654321",
                    "SecurePass@123"
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaderConstants.LOCATION, "/api/v1/accounts/" + accountId))
                    .andExpect(jsonPath("$.id").value(accountId.toString()))
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.agency").value("0001"))
                    .andExpect(jsonPath("$.number").value("123456"))
                    .andExpect(jsonPath("$.digit").value("7"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("400 Bad Request with ProblemDetail when required fields are missing")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"));
        }

        @Test
        @DisplayName("409 Conflict when CPF already exists")
        void shouldReturn409WhenCpfAlreadyExists() throws Exception {
            when(createAccountUseCase.createAccount(any(CreateAccountCommand.class)))
                    .thenThrow(new CpfAlreadyExistsException("Customer already exists with CPF: 52998224725"));

            AccountCreationRequest request = new AccountCreationRequest(
                    "Carlos Eduardo Silva",
                    "52998224725",
                    LocalDate.of(1990, 5, 15),
                    "carlos@example.com",
                    "11987654321",
                    "SecurePass@123"
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Conflict"))
                    .andExpect(jsonPath("$.detail").value(containsString("CPF")));
        }

        @Test
        @DisplayName("409 Conflict when Email already exists")
        void shouldReturn409WhenEmailAlreadyExists() throws Exception {
            when(createAccountUseCase.createAccount(any(CreateAccountCommand.class)))
                    .thenThrow(new EmailAlreadyExistsException("Customer already exists with email: carlos@example.com"));

            AccountCreationRequest request = new AccountCreationRequest(
                    "Carlos Eduardo Silva",
                    "52998224725",
                    LocalDate.of(1990, 5, 15),
                    "carlos@example.com",
                    "11987654321",
                    "SecurePass@123"
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value(containsString("email")));
        }

        @Test
        @DisplayName("409 Conflict when database unique constraint violation occurs")
        void shouldReturn409WhenDataIntegrityViolationOccurs() throws Exception {
            when(createAccountUseCase.createAccount(any(CreateAccountCommand.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uk_customer_cpf"));

            AccountCreationRequest request = new AccountCreationRequest(
                    "Carlos Eduardo Silva",
                    "52998224725",
                    LocalDate.of(1990, 5, 15),
                    "carlos@example.com",
                    "11987654321",
                    "SecurePass@123"
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Conflict"))
                    .andExpect(jsonPath("$.detail").value("Resource conflict: a record with the same unique identifier already exists"));
        }

        @Test
        @DisplayName("500 Internal Server Error when unexpected runtime exception occurs")
        void shouldReturn500WhenUnexpectedRuntimeExceptionOccurs() throws Exception {
            when(createAccountUseCase.createAccount(any(CreateAccountCommand.class)))
                    .thenThrow(new RuntimeException("Simulated unexpected failure"));

            AccountCreationRequest request = new AccountCreationRequest(
                    "Carlos Eduardo Silva",
                    "52998224725",
                    LocalDate.of(1990, 5, 15),
                    "carlos@example.com",
                    "11987654321",
                    "SecurePass@123"
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.title").value("Internal Server Error"))
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        }

        @Test
        @DisplayName("422 Unprocessable Entity when customer is underage")
        void shouldReturn422WhenCustomerUnderage() throws Exception {
            when(createAccountUseCase.createAccount(any(CreateAccountCommand.class)))
                    .thenThrow(new CustomerUnderageException("Customer must be at least 18 years old"));

            AccountCreationRequest request = new AccountCreationRequest(
                    "Menor de Idade",
                    "52998224725",
                    LocalDate.now().minusYears(16),
                    "menor@example.com",
                    "11987654321",
                    "SecurePass@123"
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is(422))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.title").value("Unprocessable Entity"));
        }

        @Test
        @DisplayName("422 Unprocessable Entity when CPF has invalid checksum")
        void shouldReturn422WhenInvalidCpfChecksum() throws Exception {
            when(createAccountUseCase.createAccount(any(CreateAccountCommand.class)))
                    .thenThrow(new InvalidCpfException("Invalid first check digit in CPF"));

            AccountCreationRequest request = new AccountCreationRequest(
                    "Carlos Eduardo Silva",
                    "12345678900",
                    LocalDate.of(1990, 5, 15),
                    "carlos@example.com",
                    "11987654321",
                    "SecurePass@123"
            );

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is(422))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(422));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/me")
    class GetAccountMeTests {

        @Test
        @DisplayName("200 OK with account data when authenticated")
        void shouldReturn200WhenAuthenticated() throws Exception {
            UUID accountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            AccountMeResult meResult = new AccountMeResult(
                    accountId,
                    customerId,
                    "0001",
                    "123456",
                    "7",
                    AccountStatus.ACTIVE,
                    Instant.parse("2026-11-09T20:00:00Z"),
                    "Carlos Eduardo Silva",
                    "52998224725",
                    "carlos@example.com"
            );
            when(getAccountMeUseCase.getAccountMe(accountId)).thenReturn(meResult);

            mockMvc.perform(get("/api/v1/accounts/me")
                            .principal(createAuthToken(customerId, accountId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(accountId.toString()))
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.agency").value("0001"))
                    .andExpect(jsonPath("$.number").value("123456"))
                    .andExpect(jsonPath("$.digit").value("7"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("401 Unauthorized with ProblemDetail when authentication is missing")
        void shouldReturn401WhenAuthenticationMissing() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/lookup (Disabled for Security)")
    class DisabledGetLookupTests {

        @Test
        @DisplayName("GET /api/v1/accounts/lookup is rejected (cannot be queried via GET)")
        void shouldRejectGetLookup() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/lookup")
                            .param("agency", "0001")
                            .param("number", "123456"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/accounts/lookup (Secure Lookup)")
    class SecurePostLookupAccountTests {

        @Test
        @DisplayName("200 OK with unmasked data when looking up by CPF")
        void shouldLookupByCpfSecurely() throws Exception {
            UUID accountId = UUID.randomUUID();
            AccountLookupResult result = new AccountLookupResult(
                    accountId,
                    "0001",
                    "100000",
                    "4",
                    "Carlos Eduardo Silva",
                    "529.982.247-25",
                    "001",
                    "Nexus Bank S.A.",
                    "C*** E*** S***"
            );

            when(lookupAccountUseCase.lookup("CPF", "52998224725", null, null)).thenReturn(result);

            AccountLookupRequest request = new AccountLookupRequest("CPF", "52998224725", null, null);

            mockMvc.perform(post("/api/v1/accounts/lookup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                    .andExpect(jsonPath("$.branch").value("0001"))
                    .andExpect(jsonPath("$.accountNumber").value("100000"))
                    .andExpect(jsonPath("$.checkDigit").value("4"))
                    .andExpect(jsonPath("$.holderName").value("Carlos Eduardo Silva"))
                    .andExpect(jsonPath("$.cpf").value("529.982.247-25"))
                    .andExpect(jsonPath("$.bankCode").value("001"))
                    .andExpect(jsonPath("$.bankName").value("Nexus Bank S.A."));
        }

        @Test
        @DisplayName("200 OK with unmasked data when looking up by ACCOUNT")
        void shouldLookupByAccountSecurely() throws Exception {
            UUID accountId = UUID.randomUUID();
            AccountLookupResult result = new AccountLookupResult(
                    accountId,
                    "0001",
                    "100000",
                    "4",
                    "Carlos Eduardo Silva",
                    "529.982.247-25",
                    "001",
                    "Nexus Bank S.A.",
                    "C*** E*** S***"
            );

            when(lookupAccountUseCase.lookup("ACCOUNT", null, "0001", "100000")).thenReturn(result);

            AccountLookupRequest request = new AccountLookupRequest("ACCOUNT", null, "0001", "100000");

            mockMvc.perform(post("/api/v1/accounts/lookup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                    .andExpect(jsonPath("$.branch").value("0001"))
                    .andExpect(jsonPath("$.accountNumber").value("100000"))
                    .andExpect(jsonPath("$.checkDigit").value("4"))
                    .andExpect(jsonPath("$.holderName").value("Carlos Eduardo Silva"))
                    .andExpect(jsonPath("$.cpf").value("529.982.247-25"))
                    .andExpect(jsonPath("$.bankCode").value("001"))
                    .andExpect(jsonPath("$.bankName").value("Nexus Bank S.A."));
        }

        @Test
        @DisplayName("404 Not Found when CPF does not exist")
        void shouldReturn404WhenCpfNotFound() throws Exception {
            when(lookupAccountUseCase.lookup("CPF", "52998224725", null, null))
                    .thenThrow(new CustomerNotFoundException("Customer not found for CPF: 52998224725"));

            AccountLookupRequest request = new AccountLookupRequest("CPF", "52998224725", null, null);

            mockMvc.perform(post("/api/v1/accounts/lookup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("400 Bad Request when search parameters are invalid")
        void shouldReturn400WhenParamsInvalid() throws Exception {
            when(lookupAccountUseCase.lookup("UNKNOWN", null, null, null))
                    .thenThrow(new IllegalArgumentException("Invalid search parameters: searchType must be CPF or ACCOUNT"));

            AccountLookupRequest request = new AccountLookupRequest("UNKNOWN", null, null, null);

            mockMvc.perform(post("/api/v1/accounts/lookup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/{accountId}")
    class GetAccountByIdTests {

        @Test
        @DisplayName("200 OK when account exists")
        void shouldReturn200WhenAccountExists() throws Exception {
            UUID accountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            AccountMeResult meResult = new AccountMeResult(
                    accountId,
                    customerId,
                    "0001",
                    "123456",
                    "7",
                    AccountStatus.ACTIVE,
                    Instant.parse("2026-11-09T20:00:00Z"),
                    "Carlos Eduardo Silva",
                    "52998224725",
                    "carlos@example.com"
            );
            when(getAccountMeUseCase.getAccountMe(accountId)).thenReturn(meResult);

            mockMvc.perform(get("/api/v1/accounts/" + accountId)
                            .principal(createAuthToken(customerId, accountId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(accountId.toString()))
                    .andExpect(jsonPath("$.agency").value("0001"))
                    .andExpect(jsonPath("$.number").value("123456"));
        }

        @Test
        @DisplayName("404 Not Found when account does not exist")
        void shouldReturn404WhenAccountNotFound() throws Exception {
            UUID accountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            when(getAccountMeUseCase.getAccountMe(accountId))
                    .thenThrow(new AccountNotFoundException("Account not found: " + accountId));

            mockMvc.perform(get("/api/v1/accounts/" + accountId)
                            .principal(createAuthToken(customerId, accountId)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("403 Forbidden with ProblemDetail when user attempts to access account of another holder")
        void shouldReturn403WhenUserIsNotOwner() throws Exception {
            UUID userAccountId = UUID.randomUUID();
            UUID targetAccountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/accounts/" + targetAccountId)
                            .principal(createAuthToken(customerId, userAccountId)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.detail").value(containsString("owner")));
        }

        @Test
        @DisplayName("200 OK when administrator accesses account of any holder")
        void shouldReturn200WhenAdminAccessesAnyAccount() throws Exception {
            UUID adminAccountId = UUID.randomUUID();
            UUID targetAccountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            AccountMeResult meResult = new AccountMeResult(
                    targetAccountId,
                    customerId,
                    "0001",
                    "654321",
                    "8",
                    AccountStatus.ACTIVE,
                    Instant.parse("2026-11-09T20:00:00Z"),
                    "Outro Cliente",
                    "12345678909",
                    "outro@example.com"
            );
            when(getAccountMeUseCase.getAccountMe(targetAccountId)).thenReturn(meResult);

            mockMvc.perform(get("/api/v1/accounts/" + targetAccountId)
                            .principal(createAuthToken(customerId, adminAccountId, "ROLE_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(targetAccountId.toString()))
                    .andExpect(jsonPath("$.agency").value("0001"))
                    .andExpect(jsonPath("$.number").value("654321"));
        }
    }

    @Nested
    @DisplayName("Decentralized RS256 JWKS Token Authentication")
    class JwksAuthenticationTests {

        @Test
        @DisplayName("200 OK when accessing /api/v1/accounts/me with valid RS256 token matching JWKS")
        void shouldAuthorizeWithValidRs256Token() throws Exception {
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();

            AccountMeResult meResult = new AccountMeResult(
                    accountId,
                    customerId,
                    "0001",
                    "123456",
                    "7",
                    AccountStatus.ACTIVE,
                    Instant.parse("2026-11-09T20:00:00Z"),
                    "Carlos Silva",
                    "52998224725",
                    "carlos@example.com"
            );
            when(getAccountMeUseCase.getAccountMe(accountId)).thenReturn(meResult);

            TokenClaims claims = new TokenClaims(customerId, accountId, "52998224725", "ROLE_USER", "0001", "123456");
            String token = rsaJwtSigner.generateToken(claims);

            mockMvc.perform(get("/api/v1/accounts/me")
                            .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(accountId.toString()))
                    .andExpect(jsonPath("$.agency").value("0001"))
                    .andExpect(jsonPath("$.number").value("123456"));
        }

        @Test
        @DisplayName("200 OK when administrator accesses another account with valid RS256 admin token")
        void shouldAuthorizeAdminAccessWithRs256Token() throws Exception {
            UUID adminAccountId = UUID.randomUUID();
            UUID targetAccountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            AccountMeResult meResult = new AccountMeResult(
                    targetAccountId,
                    customerId,
                    "0001",
                    "654321",
                    "8",
                    AccountStatus.ACTIVE,
                    Instant.parse("2026-11-09T20:00:00Z"),
                    "Target Customer",
                    "12345678909",
                    "target@example.com"
            );
            when(getAccountMeUseCase.getAccountMe(targetAccountId)).thenReturn(meResult);

            TokenClaims claims = new TokenClaims(customerId, adminAccountId, "52998224725", "ROLE_ADMIN", "0001", "123456");
            String token = rsaJwtSigner.generateToken(claims);

            mockMvc.perform(get("/api/v1/accounts/" + targetAccountId)
                            .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(targetAccountId.toString()));
        }

        @Test
        @DisplayName("403 Forbidden when regular user accesses another account with valid RS256 token")
        void shouldReturn403WhenRs256TokenUserIsNotOwner() throws Exception {
            UUID userAccountId = UUID.randomUUID();
            UUID targetAccountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            TokenClaims claims = new TokenClaims(customerId, userAccountId, "52998224725", "ROLE_USER", "0001", "123456");
            String token = rsaJwtSigner.generateToken(claims);

            mockMvc.perform(get("/api/v1/accounts/" + targetAccountId)
                            .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden"));
        }

        @Test
        @DisplayName("401 Unauthorized when RS256 token signature is signed by untrusted key")
        void shouldReturn401WhenRs256TokenSignedByUntrustedKey() throws Exception {
            RsaJwtSigner untrustedSigner = new RsaJwtSigner();
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();

            TokenClaims claims = new TokenClaims(customerId, accountId, "52998224725", "ROLE_USER", "0001", "123456");
            String untrustedToken = untrustedSigner.generateToken(claims);

            mockMvc.perform(get("/api/v1/accounts/me")
                            .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + untrustedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("401 Unauthorized when RS256 token is expired")
        void shouldReturn401WhenRs256TokenIsExpired() throws Exception {
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();

            TokenClaims claims = new TokenClaims(customerId, accountId, "52998224725", "ROLE_USER", "0001", "123456");
            String expiredToken = rsaJwtSigner.generateToken(claims, Duration.ofSeconds(-60));

            mockMvc.perform(get("/api/v1/accounts/me")
                            .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + expiredToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("401 Unauthorized when Bearer token is malformed")
        void shouldReturn401WhenTokenIsMalformed() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/me")
                            .header(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + "malformed.jwt.token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401));
        }
    }
}
