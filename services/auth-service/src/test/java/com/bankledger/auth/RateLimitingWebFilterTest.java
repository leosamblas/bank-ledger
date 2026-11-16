package com.bankledger.auth;

import com.bankledger.auth.config.RateLimitingWebFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitingWebFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private WebFilterChain filterChain;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private RateLimitingWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingWebFilter(java.util.Optional.of(redisTemplate), objectMapper);
    }

    @Test
    @DisplayName("Should bypass rate limiting for actuator and health endpoints")
    void shouldBypassActuatorEndpoints() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build()
        );
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(exchange);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("Should allow request when TPS is within default limit (20)")
    void shouldAllowRequestWithinLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build()
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Config key not set -> default 20
        when(valueOperations.get(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY)).thenReturn(Mono.empty());
        // 10th request this second
        when(valueOperations.increment(startsWith("auth:ratelimit:"))).thenReturn(Mono.just(10L));
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should return 429 Too Many Requests with Retry-After: 1 when TPS exceeds limit")
    void shouldBlockWhenTpsExceeded() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build()
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY)).thenReturn(Mono.empty());
        // 21st request this second (exceeds default 20)
        when(valueOperations.increment(startsWith("auth:ratelimit:"))).thenReturn(Mono.just(21L));

        filter.filter(exchange, filterChain).block();

        verify(filterChain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(exchange.getResponse().getHeaders().getContentType().toString()).contains("application/problem+json");
    }

    @Test
    @DisplayName("Should honor dynamically configured TPS limit from Redis")
    void shouldHonorDynamicTpsLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build()
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Dynamic TPS set to 5
        when(valueOperations.get(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY)).thenReturn(Mono.just("5"));
        // 6th request this second -> exceeds dynamic 5
        when(valueOperations.increment(startsWith("auth:ratelimit:"))).thenReturn(Mono.just(6L));

        filter.filter(exchange, filterChain).block();

        verify(filterChain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Should gracefully allow request when Redis is unavailable")
    void shouldAllowWhenRedisFails() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build()
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RateLimitingWebFilter.RATELIMIT_CONFIG_KEY))
                .thenReturn(Mono.error(new RuntimeException("Redis timeout")));
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(exchange);
    }
}
