package com.bankledger.account.adapters.in.rest;

import com.bankledger.account.adapters.in.rest.dto.AccountCreationRequest;
import com.bankledger.account.adapters.in.rest.dto.AccountLookupResponse;
import com.bankledger.account.adapters.in.rest.dto.AccountResponse;
import com.bankledger.account.application.port.in.*;
import com.bankledger.account.domain.exception.*;
import com.bankledger.account.domain.model.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @BeforeEach
    void setUp() {
        accountController = new AccountController(createAccountUseCase, getAccountMeUseCase, lookupAccountUseCase);
        globalExceptionHandler = new GlobalExceptionHandler();

        mockMvc = MockMvcBuilders
                .standaloneSetup(accountController)
                .setControllerAdvice(globalExceptionHandler)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .build();
    }

    private UsernamePasswordAuthenticationToken createAuthToken(UUID customerId, UUID accountId) {
        AuthenticatedUser user = new AuthenticatedUser(customerId, accountId, "0001", "123456", "ROLE_USER");
        return new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Nested
    @DisplayName("POST /api/v1/accounts")
    class CreateAccountTests {

        @Test
        @DisplayName("201 Created com header Location quando dados são válidos")
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
                    .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/accounts/" + accountId))
                    .andExpect(jsonPath("$.id").value(accountId.toString()))
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.agency").value("0001"))
                    .andExpect(jsonPath("$.number").value("123456"))
                    .andExpect(jsonPath("$.digit").value("7"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("400 Bad Request com ProblemDetail quando campos obrigatórios estão ausentes")
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
        @DisplayName("409 Conflict quando CPF já existe")
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
        @DisplayName("409 Conflict quando Email já existe")
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
        @DisplayName("422 Unprocessable Entity quando cliente é menor de idade")
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
        @DisplayName("422 Unprocessable Entity quando CPF possui checksum inválido")
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
        @DisplayName("200 OK com dados da conta quando autenticado")
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
        @DisplayName("401 Unauthorized com ProblemDetail quando autenticação está ausente")
        void shouldReturn401WhenAuthenticationMissing() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/lookup")
    class LookupAccountTests {

        @Test
        @DisplayName("200 OK com nome mascarado quando conta existe")
        void shouldReturn200WithMaskedNameWhenAccountExists() throws Exception {
            UUID accountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            AccountLookupResult lookupResult = new AccountLookupResult(
                    accountId,
                    "0001",
                    "123456",
                    "7",
                    "C*** E*** S***"
            );
            when(lookupAccountUseCase.lookup("0001", "123456")).thenReturn(lookupResult);

            mockMvc.perform(get("/api/v1/accounts/lookup")
                            .principal(createAuthToken(customerId, accountId))
                            .param("agency", "0001")
                            .param("number", "123456"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                    .andExpect(jsonPath("$.agency").value("0001"))
                    .andExpect(jsonPath("$.number").value("123456"))
                    .andExpect(jsonPath("$.holderNameMasked").value("C*** E*** S***"));
        }

        @Test
        @DisplayName("404 Not Found com ProblemDetail quando conta não existe")
        void shouldReturn404WhenAccountNotFound() throws Exception {
            UUID accountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            when(lookupAccountUseCase.lookup("0001", "999999"))
                    .thenThrow(new AccountNotFoundException("Account not found for 0001/999999"));

            mockMvc.perform(get("/api/v1/accounts/lookup")
                            .principal(createAuthToken(customerId, accountId))
                            .param("agency", "0001")
                            .param("number", "999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Not Found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/{accountId}")
    class GetAccountByIdTests {

        @Test
        @DisplayName("200 OK quando conta existe")
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
        @DisplayName("404 Not Found quando conta não existe")
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
    }
}
