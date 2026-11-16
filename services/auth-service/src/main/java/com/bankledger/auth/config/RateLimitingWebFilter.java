package com.bankledger.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reactive WebFilter providing dynamic rate limiting using ReactiveStringRedisTemplate.
 * Tracks requests per second per Redis key 'auth:ratelimit:{epochSecond}'.
 * Default limit is 20 TPS, dynamically configurable via Redis key 'auth:ratelimit:config:tps'.
 * Excludes health/actuator and OpenAPI endpoints.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitingWebFilter implements WebFilter {

    public static final String RATELIMIT_CONFIG_KEY = "auth:ratelimit:config:tps";
    public static final String RATELIMIT_KEY_PREFIX = "auth:ratelimit:";
    public static final int DEFAULT_TPS = 20;

    private final Optional<ReactiveStringRedisTemplate> redisTemplateOptional;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        ReactiveStringRedisTemplate redis = redisTemplateOptional.orElse(null);
        if (isExcluded(path) || redis == null) {
            return chain.filter(exchange);
        }

        return getEffectiveTps(redis)
                .flatMap(limit -> checkRateLimit(exchange, chain, limit, redis))
                .onErrorResume(e -> {
                    log.warn("Rate limiting Redis operation failed gracefully: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    private boolean isExcluded(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars");
    }

    private Mono<Integer> getEffectiveTps(ReactiveStringRedisTemplate redis) {
        return redis.opsForValue().get(RATELIMIT_CONFIG_KEY)
                .map(val -> {
                    try {
                        int parsed = Integer.parseInt(val);
                        return parsed > 0 ? parsed : DEFAULT_TPS;
                    } catch (NumberFormatException e) {
                        return DEFAULT_TPS;
                    }
                })
                .defaultIfEmpty(DEFAULT_TPS)
                .onErrorReturn(DEFAULT_TPS);
    }

    private Mono<Void> checkRateLimit(ServerWebExchange exchange, WebFilterChain chain, int limit, ReactiveStringRedisTemplate redis) {
        long epochSecond = Instant.now().getEpochSecond();
        String windowKey = RATELIMIT_KEY_PREFIX + epochSecond;

        return redis.opsForValue().increment(windowKey)
                .flatMap(count -> {
                    if (count == 1) {
                        redis.expire(windowKey, Duration.ofSeconds(5))
                                .subscribe(null, e -> log.warn("Failed to set TTL on rate limit key {}: {}", windowKey, e.getMessage()));
                    }
                    if (count > limit) {
                        log.warn("Rate limit exceeded for path {}: count={}, limit={}", exchange.getRequest().getPath(), count, limit);
                        return writeTooManyRequestsResponse(exchange, limit);
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> writeTooManyRequestsResponse(ServerWebExchange exchange, int limit) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, "1");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Maximum allowed: " + limit + " requests per second."
        );
        problem.setTitle("Too Many Requests");
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(problem);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
