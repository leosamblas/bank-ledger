package com.bankledger.account.adapters.in.rest;

import com.bankledger.account.adapters.in.rest.dto.UpdateProfileRequest;
import com.bankledger.account.application.port.in.AccountMeResult;
import com.bankledger.account.application.port.in.CreateAccountUseCase;
import com.bankledger.account.application.port.in.GetAccountMeUseCase;
import com.bankledger.account.application.port.in.LookupAccountUseCase;
import com.bankledger.account.application.port.in.UpdateProfileCommand;
import com.bankledger.account.application.port.in.UpdateProfileUseCase;
import com.bankledger.account.domain.exception.EmailAlreadyExistsException;
import com.bankledger.account.domain.exception.InvalidAddressException;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.account.domain.vo.Address;
import com.bankledger.commons.security.CommonAuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountProfileControllerTest {

    private MockMvc mockMvc;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private CreateAccountUseCase createAccountUseCase;

    @Mock
    private GetAccountMeUseCase getAccountMeUseCase;

    @Mock
    private LookupAccountUseCase lookupAccountUseCase;

    @Mock
    private UpdateProfileUseCase updateProfileUseCase;

    private AccountController accountController;
    private GlobalExceptionHandler globalExceptionHandler;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        accountController = new AccountController(
                createAccountUseCase,
                getAccountMeUseCase,
                lookupAccountUseCase,
                updateProfileUseCase
        );
        globalExceptionHandler = new GlobalExceptionHandler();

        mockMvc = MockMvcBuilders
                .standaloneSetup(accountController)
                .setControllerAdvice(globalExceptionHandler)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .build();
    }

    private UsernamePasswordAuthenticationToken createAuthToken() {
        CommonAuthenticatedUser user = new CommonAuthenticatedUser(
                CUSTOMER_ID, ACCOUNT_ID, "52998224725", "ROLE_USER", "0001", "100000"
        );
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Nested
    @DisplayName("PUT /api/v1/accounts/me/profile")
    class UpdateProfileTests {

        @Test
        @DisplayName("200 OK when updating profile with valid data while preserving immutable fields")
        void shouldUpdateProfileSuccessfully() throws Exception {
            Address address = Address.of(
                    "01310-100",
                    "Avenida Paulista",
                    "1000",
                    "Apto 101",
                    "Bela Vista",
                    "São Paulo",
                    "SP"
            );

            AccountMeResult updatedResult = new AccountMeResult(
                    ACCOUNT_ID,
                    CUSTOMER_ID,
                    "0001",
                    "100000",
                    "4",
                    AccountStatus.ACTIVE,
                    NOW,
                    "Carlos Eduardo Silva",
                    "52998224725",
                    "new.email@example.com",
                    "+5511999998888",
                    address,
                    5_000_000L
            );

            when(updateProfileUseCase.updateProfile(any(UpdateProfileCommand.class))).thenReturn(updatedResult);

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "+5511999998888",
                    "new.email@example.com",
                    "01310-100",
                    "Avenida Paulista",
                    "1000",
                    "Apto 101",
                    "Bela Vista",
                    "São Paulo",
                    "SP"
            );

            mockMvc.perform(put("/api/v1/accounts/me/profile")
                            .principal(createAuthToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
                    .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                    .andExpect(jsonPath("$.holderName").value("Carlos Eduardo Silva"))
                    .andExpect(jsonPath("$.cpf").value("52998224725"))
                    .andExpect(jsonPath("$.email").value("new.email@example.com"))
                    .andExpect(jsonPath("$.phone").value("+5511999998888"))
                    .andExpect(jsonPath("$.address.postalCode").value("01310-100"))
                    .andExpect(jsonPath("$.address.street").value("Avenida Paulista"))
                    .andExpect(jsonPath("$.address.number").value("1000"))
                    .andExpect(jsonPath("$.address.complement").value("Apto 101"))
                    .andExpect(jsonPath("$.address.neighborhood").value("Bela Vista"))
                    .andExpect(jsonPath("$.address.city").value("São Paulo"))
                    .andExpect(jsonPath("$.address.state").value("SP"))
                    .andExpect(jsonPath("$.dailyTransferLimitCents").value(5000000));

            ArgumentCaptor<UpdateProfileCommand> captor = ArgumentCaptor.forClass(UpdateProfileCommand.class);
            verify(updateProfileUseCase).updateProfile(captor.capture());
            UpdateProfileCommand captured = captor.getValue();
            assertThat(captured.accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(captured.email()).isEqualTo("new.email@example.com");
            assertThat(captured.phone()).isEqualTo("+5511999998888");
            assertThat(captured.street()).isEqualTo("Avenida Paulista");
        }

        @Test
        @DisplayName("401 Unauthorized when authentication is missing on profile update")
        void shouldReturn401WhenUnauthenticated() throws Exception {
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "+5511999998888",
                    "new.email@example.com",
                    "01310-100",
                    "Avenida Paulista",
                    "1000",
                    null,
                    "Bela Vista",
                    "São Paulo",
                    "SP"
            );

            mockMvc.perform(put("/api/v1/accounts/me/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("409 Conflict when new email is already taken by another user")
        void shouldReturn409WhenEmailAlreadyExists() throws Exception {
            when(updateProfileUseCase.updateProfile(any(UpdateProfileCommand.class)))
                    .thenThrow(new EmailAlreadyExistsException("Email already in use: taken@example.com"));

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "+5511999998888",
                    "taken@example.com",
                    "01310-100",
                    "Avenida Paulista",
                    "1000",
                    null,
                    "Bela Vista",
                    "São Paulo",
                    "SP"
            );

            mockMvc.perform(put("/api/v1/accounts/me/profile")
                            .principal(createAuthToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("422 Unprocessable Entity when address violates validation")
        void shouldReturn422WhenAddressInvalid() throws Exception {
            when(updateProfileUseCase.updateProfile(any(UpdateProfileCommand.class)))
                    .thenThrow(new InvalidAddressException("Invalid Brazilian state (UF): XX"));

            UpdateProfileRequest request = new UpdateProfileRequest(
                    "+5511999998888",
                    "valid@example.com",
                    "01310-100",
                    "Avenida Paulista",
                    "1000",
                    null,
                    "Bela Vista",
                    "São Paulo",
                    "XX"
            );

            mockMvc.perform(put("/api/v1/accounts/me/profile")
                            .principal(createAuthToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is(422));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/accounts/me enhanced")
    class GetAccountMeEnhancedTests {

        @Test
        @DisplayName("200 OK returns enhanced AccountResponse with address and dailyTransferLimitCents")
        void shouldReturnEnhancedAccountResponse() throws Exception {
            Address address = Address.of(
                    "01310-100",
                    "Avenida Paulista",
                    "1000",
                    "Apto 101",
                    "Bela Vista",
                    "São Paulo",
                    "SP"
            );

            AccountMeResult meResult = new AccountMeResult(
                    ACCOUNT_ID,
                    CUSTOMER_ID,
                    "0001",
                    "100000",
                    "4",
                    AccountStatus.ACTIVE,
                    NOW,
                    "Carlos Eduardo Silva",
                    "52998224725",
                    "carlos@example.com",
                    "+5511987654321",
                    address,
                    10_000_000L
            );

            when(getAccountMeUseCase.getAccountMe(ACCOUNT_ID)).thenReturn(meResult);

            mockMvc.perform(get("/api/v1/accounts/me")
                            .principal(createAuthToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
                    .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                    .andExpect(jsonPath("$.agency").value("0001"))
                    .andExpect(jsonPath("$.number").value("100000"))
                    .andExpect(jsonPath("$.digit").value("4"))
                    .andExpect(jsonPath("$.holderName").value("Carlos Eduardo Silva"))
                    .andExpect(jsonPath("$.cpf").value("52998224725"))
                    .andExpect(jsonPath("$.email").value("carlos@example.com"))
                    .andExpect(jsonPath("$.phone").value("+5511987654321"))
                    .andExpect(jsonPath("$.address.postalCode").value("01310-100"))
                    .andExpect(jsonPath("$.address.street").value("Avenida Paulista"))
                    .andExpect(jsonPath("$.dailyTransferLimitCents").value(10000000));
        }
    }
}
