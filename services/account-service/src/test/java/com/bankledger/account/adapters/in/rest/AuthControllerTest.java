package com.bankledger.account.adapters.in.rest;

import com.bankledger.account.adapters.in.rest.dto.LoginRequest;
import com.bankledger.account.application.port.in.LoginCommand;
import com.bankledger.account.application.port.in.LoginResult;
import com.bankledger.account.application.port.in.LoginUseCase;
import com.bankledger.account.domain.exception.CustomerLockedException;
import com.bankledger.account.domain.exception.InvalidCredentialsException;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private LoginUseCase loginUseCase;

    private AuthController authController;
    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        authController = new AuthController(loginUseCase);
        globalExceptionHandler = new GlobalExceptionHandler();

        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(globalExceptionHandler)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("200 OK with token and expiresIn when credentials are valid")
    void shouldReturn200WithTokenWhenCredentialsValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        LoginResult loginResult = new LoginResult(
                "mocked.jwt.token",
                3600L,
                customerId,
                accountId,
                "0001",
                "123456",
                "Carlos Eduardo Silva"
        );
        when(loginUseCase.login(any(LoginCommand.class))).thenReturn(loginResult);

        LoginRequest request = new LoginRequest("52998224725", "Secret@123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
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
                .thenThrow(new CustomerLockedException("Conta bloqueada temporariamente por excesso de tentativas"));

        LoginRequest request = new LoginRequest("52998224725", "Secret@123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value(containsString("bloqueada")));
    }

    @Test
    @DisplayName("400 Bad Request with ProblemDetail when required fields are missing")
    void shouldReturn400WhenMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }
}
