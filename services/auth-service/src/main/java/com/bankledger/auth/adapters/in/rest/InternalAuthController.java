package com.bankledger.auth.adapters.in.rest;

import com.bankledger.auth.adapters.in.rest.dto.AuthUserSummaryResponse;
import com.bankledger.auth.adapters.in.rest.dto.RateLimitConfigRequest;
import com.bankledger.auth.adapters.in.rest.dto.RateLimitConfigResponse;
import com.bankledger.auth.adapters.in.rest.dto.RegisterCredentialsRequest;
import com.bankledger.auth.application.port.in.FindAuthUserUseCase;
import com.bankledger.auth.application.port.in.RegisterCredentialsCommand;
import com.bankledger.auth.application.port.in.RegisterCredentialsUseCase;
import com.bankledger.auth.config.AuthSecurityProperties;
import com.bankledger.auth.config.RateLimitingWebFilter;
import com.bankledger.commons.security.SecurityConstants;
import com.bankledger.commons.security.dto.RegisterCredentialsPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalAuthController {

    public static final String DEFAULT_INTERNAL_SERVICE_KEY = "bankledger-internal-secret-ledger-key-2026";

    private final RegisterCredentialsUseCase registerCredentialsUseCase;
    private final Optional<FindAuthUserUseCase> findAuthUserUseCase;
    private final Optional<ReactiveStringRedisTemplate> redisTemplate;
    private final Clock clock;
    private final AuthSecurityProperties properties;

    public record CredentialResponse(UUID id, UUID customerId, String cpf) {}

    private void validateInternalServiceKey(String headerKey) {
        String expectedKey = (properties != null && properties.getInternalServiceKey() != null)
                ? properties.getInternalServiceKey()
                : DEFAULT_INTERNAL_SERVICE_KEY;
        if (headerKey == null || !headerKey.equals(expectedKey)) {
            log.warn("Unauthorized internal request: invalid or missing X-Internal-Service-Key");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing " + SecurityConstants.INTERNAL_SERVICE_KEY_HEADER + " header");
        }
    }

    @PostMapping(SecurityConstants.INTERNAL_AUTH_CREDENTIALS_PATH)
    public Mono<ResponseEntity<CredentialResponse>> registerCredentials(@Valid @RequestBody RegisterCredentialsRequest request) {
        log.info("Registering internal credentials for customerId: {}", request.customerId());
        RegisterCredentialsPayload payload = request.toPayload();
        return Mono.fromCallable(() -> registerCredentialsUseCase.registerCredentials(RegisterCredentialsCommand.fromPayload(payload)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(user -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new CredentialResponse(user.getId(), user.getCustomerId(), user.getCpf())));
    }

    @GetMapping("/internal/auth/users/{customerId}")
    public Mono<ResponseEntity<AuthUserSummaryResponse>> getUserByCustomerId(
            @PathVariable UUID customerId,
            @RequestHeader(value = SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, required = false) String serviceKey
    ) {
        validateInternalServiceKey(serviceKey);

        if (findAuthUserUseCase.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        return Mono.fromCallable(() -> findAuthUserUseCase.get().findByCustomerId(customerId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(maybeUser -> maybeUser
                        .map(user -> ResponseEntity.ok(AuthUserSummaryResponse.fromDomain(user, Instant.now(clock))))
                        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    @GetMapping("/internal/auth/users/by-cpf/{cpf}")
    public Mono<ResponseEntity<AuthUserSummaryResponse>> getUserByCpf(
            @PathVariable String cpf,
            @RequestHeader(value = SecurityConstants.INTERNAL_SERVICE_KEY_HEADER, required = false) String serviceKey
    ) {
        validateInternalServiceKey(serviceKey);

        if (findAuthUserUseCase.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        return Mono.fromCallable(() -> findAuthUserUseCase.get().findByCpf(cpf))
                .subscribeOn(Schedulers.boundedElastic())
                .map(maybeUser -> maybeUser
                        .map(user -> ResponseEntity.ok(AuthUserSummaryResponse.fromDomain(user, Instant.now(clock))))
                        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    @PutMapping("/internal/auth/ratelimit")
    public Mono<ResponseEntity<RateLimitConfigResponse>> updateRateLimitPut(@Valid @RequestBody RateLimitConfigRequest request) {
        return applyRateLimitUpdate(request);
    }

    @PostMapping("/internal/auth/ratelimit")
    public Mono<ResponseEntity<RateLimitConfigResponse>> updateRateLimitPost(@Valid @RequestBody RateLimitConfigRequest request) {
        return applyRateLimitUpdate(request);
    }

    private Mono<ResponseEntity<RateLimitConfigResponse>> applyRateLimitUpdate(RateLimitConfigRequest request) {
        log.info("Updating rate limit TPS configuration to: {}", request.tps());
        if (redisTemplate.isEmpty()) {
            return Mono.just(ResponseEntity.ok(new RateLimitConfigResponse(request.tps())));
        }

        return redisTemplate.get().opsForValue()
                .set(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY, String.valueOf(request.tps()))
                .map(success -> ResponseEntity.ok(new RateLimitConfigResponse(request.tps())))
                .onErrorResume(e -> {
                    log.error("Failed to update rate limit in Redis: {}", e.getMessage());
                    return Mono.just(ResponseEntity.ok(new RateLimitConfigResponse(request.tps())));
                });
    }

    @GetMapping("/internal/auth/ratelimit")
    public Mono<ResponseEntity<RateLimitConfigResponse>> getRateLimit() {
        if (redisTemplate.isEmpty()) {
            return Mono.just(ResponseEntity.ok(new RateLimitConfigResponse(RateLimitingWebFilter.DEFAULT_TPS)));
        }

        return redisTemplate.get().opsForValue()
                .get(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY)
                .map(val -> {
                    try {
                        return ResponseEntity.ok(new RateLimitConfigResponse(Integer.parseInt(val)));
                    } catch (NumberFormatException e) {
                        return ResponseEntity.ok(new RateLimitConfigResponse(RateLimitingWebFilter.DEFAULT_TPS));
                    }
                })
                .defaultIfEmpty(ResponseEntity.ok(new RateLimitConfigResponse(RateLimitingWebFilter.DEFAULT_TPS)))
                .onErrorReturn(ResponseEntity.ok(new RateLimitConfigResponse(RateLimitingWebFilter.DEFAULT_TPS)));
    }
}
