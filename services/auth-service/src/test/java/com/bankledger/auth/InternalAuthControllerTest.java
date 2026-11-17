package com.bankledger.auth;

import com.bankledger.auth.adapters.in.rest.GlobalExceptionHandler;
import com.bankledger.auth.adapters.in.rest.InternalAuthController;
import com.bankledger.auth.adapters.in.rest.dto.RateLimitConfigRequest;
import com.bankledger.auth.application.port.in.FindAuthUserUseCase;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.auth.config.AuthSecurityProperties;
import com.bankledger.auth.config.RateLimitingWebFilter;
import com.bankledger.auth.domain.model.AuthUser;
import com.bankledger.commons.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAuthControllerTest {

    private static final String SERVICE_KEY = "test-internal-service-key-2026";
    private static final Instant T0 = Instant.parse("2026-11-16T12:00:00Z");

    @Mock
    private RegisterCredentialsUseCase registerCredentialsUseCase;

    @Mock
    private FindAuthUserUseCase findAuthUserUseCase;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setInternalServiceKey(SERVICE_KEY);
        InternalAuthController controller = new InternalAuthController(
                registerCredentialsUseCase,
                Optional.ofNullable(findAuthUserUseCase),
                Optional.ofNullable(redisTemplate),
                clock,
                properties
        );

        client = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /internal/auth/users/{customerId}: 200 OK with sanitized summary when X-Internal-Service-Key is valid")
    void shouldReturn200WithSanitizedSummaryByCustomerId() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthUser user = new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$2a$10$supersecretpasswordhash",
                "ROLE_USER",
                customerId,
                accountId,
                "0001",
                "123456",
                0,
                null,
                "Carlos Silva",
                "carlos@example.com",
                T0,
                T0
        );

        when(findAuthUserUseCase.findByCustomerId(customerId)).thenReturn(Optional.of(user));

        client.get()
                .uri("/internal/auth/users/" + customerId)
                .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, SERVICE_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.customerId").isEqualTo(customerId.toString())
                .jsonPath("$.cpf").isEqualTo("52998224725")
                .jsonPath("$.fullName").isEqualTo("Carlos Silva")
                .jsonPath("$.email").isEqualTo("carlos@example.com")
                .jsonPath("$.role").isEqualTo("ROLE_USER")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.accountId").isEqualTo(accountId.toString())
                .jsonPath("$.branch").isEqualTo("0001")
                .jsonPath("$.accountNumber").isEqualTo("123456")
                .jsonPath("$.passwordHash").doesNotExist()
                .jsonPath("$.password").doesNotExist()
                .jsonPath("$.failedLoginAttempts").doesNotExist()
                .jsonPath("$.lockedUntil").doesNotExist()
                .jsonPath("$.activeToken").doesNotExist()
                .jsonPath("$.refreshToken").doesNotExist();
    }

    @Test
    @DisplayName("GET /internal/auth/users/{customerId}: 401 Unauthorized when X-Internal-Service-Key is missing or invalid")
    void shouldReturn401WhenServiceKeyMissingOrInvalidForCustomerId() {
        UUID customerId = UUID.randomUUID();

        // Missing header
        client.get()
                .uri("/internal/auth/users/" + customerId)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        // Invalid header
        client.get()
                .uri("/internal/auth/users/" + customerId)
                .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, "wrong-key")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("GET /internal/auth/users/{customerId}: 404 Not Found when user does not exist")
    void shouldReturn404WhenCustomerIdNotFound() {
        UUID customerId = UUID.randomUUID();
        when(findAuthUserUseCase.findByCustomerId(customerId)).thenReturn(Optional.empty());

        client.get()
                .uri("/internal/auth/users/" + customerId)
                .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, SERVICE_KEY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET /internal/auth/users/by-cpf/{cpf}: 200 OK with sanitized summary when X-Internal-Service-Key is valid")
    void shouldReturn200WithSanitizedSummaryByCpf() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthUser user = new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$2a$10$supersecretpasswordhash",
                "ROLE_USER",
                customerId,
                accountId,
                "0001",
                "123456",
                0,
                null,
                "Carlos Silva",
                "carlos@example.com",
                T0,
                T0
        );

        when(findAuthUserUseCase.findByCpf("52998224725")).thenReturn(Optional.of(user));

        client.get()
                .uri("/internal/auth/users/by-cpf/52998224725")
                .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, SERVICE_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.customerId").isEqualTo(customerId.toString())
                .jsonPath("$.cpf").isEqualTo("52998224725")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.passwordHash").doesNotExist();
    }

    @Test
    @DisplayName("GET /internal/auth/users/by-cpf/{cpf}: 401 Unauthorized when X-Internal-Service-Key is missing")
    void shouldReturn401WhenServiceKeyMissingForCpf() {
        client.get()
                .uri("/internal/auth/users/by-cpf/52998224725")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /internal/auth/users/by-cpf/{cpf}: reports status LOCKED when lockedUntil is in the future")
    void shouldReportLockedStatusWhenAccountLocked() {
        UUID customerId = UUID.randomUUID();
        AuthUser lockedUser = new AuthUser(
                UUID.randomUUID(),
                "52998224725",
                "$2a$10$supersecretpasswordhash",
                "ROLE_USER",
                customerId,
                UUID.randomUUID(),
                "0001",
                "123456",
                5,
                T0.plusSeconds(600), // locked for 10 more minutes
                "Carlos Silva",
                "carlos@example.com",
                T0,
                T0
        );

        when(findAuthUserUseCase.findByCpf("52998224725")).thenReturn(Optional.of(lockedUser));

        client.get()
                .uri("/internal/auth/users/by-cpf/52998224725")
                .header(SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, SERVICE_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("PUT /internal/auth/ratelimit: updates rate limit TPS configuration in Redis")
    void shouldUpdateRateLimitTpsInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY, "50")).thenReturn(Mono.just(true));

        client.put()
                .uri("/internal/auth/ratelimit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RateLimitConfigRequest(50))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tps").isEqualTo(50);

        verify(valueOperations).set(eq(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY), eq("50"));
    }

    @Test
    @DisplayName("POST /internal/auth/ratelimit: updates rate limit TPS configuration in Redis")
    void shouldUpdateRateLimitViaPost() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY, "30")).thenReturn(Mono.just(true));

        client.post()
                .uri("/internal/auth/ratelimit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RateLimitConfigRequest(30))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tps").isEqualTo(30);
    }
}
