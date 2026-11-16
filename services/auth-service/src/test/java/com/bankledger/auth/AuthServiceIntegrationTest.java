package com.bankledger.auth;

import com.bankledger.auth.adapters.in.rest.dto.IntrospectRequest;
import com.bankledger.auth.adapters.in.rest.dto.LoginRequest;
import com.bankledger.auth.adapters.in.rest.dto.RegisterCredentialsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class AuthServiceIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("End-to-End: Provision credentials -> Retrieve JWKS -> Login -> Introspect -> Reject wrong password")
    void shouldExecuteFullAuthenticationLifecycle() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String cpf = "78912345601";
        String password = "StrongPassword@2026";

        // 1. Provision credentials via internal API
        RegisterCredentialsRequest registerRequest = new RegisterCredentialsRequest(
                customerId,
                cpf,
                password,
                accountId,
                "0001",
                "987654",
                "Maria Souza",
                "maria@example.com"
        );

        mockMvc.perform(post("/internal/v1/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()));

        // 2. Retrieve JWKS
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray());

        // 3. Login with provisioned credentials
        LoginRequest loginRequest = new LoginRequest(cpf, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.cpf").value(cpf))
                .andExpect(jsonPath("$.user.fullName").value("Maria Souza"))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.path("token").asString();
        assertThat(token).isNotEmpty();

        // 4. Introspect token
        IntrospectRequest introspectRequest = new IntrospectRequest(token);
        mockMvc.perform(post("/api/v1/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(introspectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sub").value(cpf))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));

        // 5. Reject wrong password
        LoginRequest wrongLoginRequest = new LoginRequest(cpf, "WrongPassword@000");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongLoginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(containsString("Credenciais inválidas")));
    }
}
