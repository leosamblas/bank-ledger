package com.bankledger.auth;

import com.bankledger.auth.adapters.in.rest.dto.IntrospectRequest;
import com.bankledger.auth.adapters.in.rest.dto.LoginRequest;
import com.bankledger.auth.adapters.in.rest.dto.RefreshTokenRequest;
import com.bankledger.auth.adapters.in.rest.dto.RegisterCredentialsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class AuthServiceIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient
                .bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    @DisplayName("End-to-End: Provision -> JWKS -> Login -> Token Deduplication -> Refresh -> Introspect -> Reject wrong password")
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

        client.post()
                .uri("/internal/v1/auth/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.cpf").isEqualTo(cpf)
                .jsonPath("$.customerId").isEqualTo(customerId.toString());

        // 2. Retrieve JWKS
        client.get()
                .uri("/.well-known/jwks.json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.keys").isArray();

        // 3. Login with provisioned credentials
        LoginRequest loginRequest = new LoginRequest(cpf, password);
        byte[] loginBody = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty()
                .jsonPath("$.user.cpf").isEqualTo(cpf)
                .jsonPath("$.user.fullName").isEqualTo("Maria Souza")
                .returnResult()
                .getResponseBody();

        assertThat(loginBody).isNotNull();
        JsonNode loginJson = objectMapper.readTree(loginBody);
        String initialToken = loginJson.path("token").asString();
        String initialRefreshToken = loginJson.path("refreshToken").asString();
        long initialExpiresIn = loginJson.path("expiresIn").asLong();
        assertThat(initialToken).isNotEmpty();
        assertThat(initialRefreshToken).isNotEmpty();

        // 4. Token Deduplication & Dynamic Expiration: second login must return SAME active token
        byte[] duplicateLoginBody = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(duplicateLoginBody).isNotNull();
        JsonNode duplicateLoginJson = objectMapper.readTree(duplicateLoginBody);
        String secondToken = duplicateLoginJson.path("token").asString();
        long secondExpiresIn = duplicateLoginJson.path("expiresIn").asLong();

        assertThat(secondToken).isEqualTo(initialToken);
        assertThat(secondExpiresIn).isLessThanOrEqualTo(initialExpiresIn);

        // 5. Refresh token flow
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(initialRefreshToken);
        byte[] refreshBody = client.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refreshRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty()
                .returnResult()
                .getResponseBody();

        assertThat(refreshBody).isNotNull();
        JsonNode refreshJson = objectMapper.readTree(refreshBody);
        String rotatedToken = refreshJson.path("token").asString();
        String rotatedRefreshToken = refreshJson.path("refreshToken").asString();

        assertThat(rotatedToken).isNotEqualTo(initialToken);
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);

        // 6. Introspect rotated token
        IntrospectRequest introspectRequest = new IntrospectRequest(rotatedToken);
        client.post()
                .uri("/api/v1/auth/introspect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(introspectRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.sub").isEqualTo(cpf)
                .jsonPath("$.customerId").isEqualTo(customerId.toString())
                .jsonPath("$.accountId").isEqualTo(accountId.toString());

        // 7. Reject wrong password
        LoginRequest wrongLoginRequest = new LoginRequest(cpf, "WrongPassword@000");
        client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(wrongLoginRequest)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").value(detail -> org.assertj.core.api.Assertions.assertThat(detail.toString()).contains("Credenciais inválidas"));
    }
}
