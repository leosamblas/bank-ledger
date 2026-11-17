package com.bankledger.auth;

import com.bankledger.auth.adapters.out.redis.RedisAccountLockoutAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisAccountLockoutAdapterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RedisAccountLockoutAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisAccountLockoutAdapter(Optional.of(redisTemplate));
    }

    @Test
    @DisplayName("isLocked: returns true when Redis key auth:lock:{cpf} exists")
    void shouldReturnTrueWhenLockedInRedis() {
        String cpf = "52998224725";
        when(redisTemplate.hasKey("auth:lock:" + cpf)).thenReturn(Mono.just(true));

        boolean locked = adapter.isLocked(cpf);

        assertThat(locked).isTrue();
        verify(redisTemplate).hasKey("auth:lock:" + cpf);
    }

    @Test
    @DisplayName("isLocked: returns false when Redis key auth:lock:{cpf} does not exist")
    void shouldReturnFalseWhenNotLockedInRedis() {
        String cpf = "52998224725";
        when(redisTemplate.hasKey("auth:lock:" + cpf)).thenReturn(Mono.just(false));

        boolean locked = adapter.isLocked(cpf);

        assertThat(locked).isFalse();
    }

    @Test
    @DisplayName("isLocked: graceful fallback returning false when Redis throws an exception")
    void shouldReturnFalseGracefullyWhenRedisFails() {
        String cpf = "52998224725";
        when(redisTemplate.hasKey("auth:lock:" + cpf)).thenReturn(Mono.error(new RuntimeException("Redis connection error")));

        boolean locked = adapter.isLocked(cpf);

        assertThat(locked).isFalse();
    }

    @Test
    @DisplayName("recordFailedAttempt: increments failed attempts and sets TTL 900s")
    void shouldIncrementFailedAttemptsWithTtl() {
        String cpf = "52998224725";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:failed_attempts:" + cpf)).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq("auth:failed_attempts:" + cpf), eq(Duration.ofSeconds(900)))).thenReturn(Mono.just(true));

        long count = adapter.recordFailedAttempt(cpf);

        assertThat(count).isEqualTo(1L);
        verify(valueOperations).increment("auth:failed_attempts:" + cpf);
        verify(redisTemplate).expire(eq("auth:failed_attempts:" + cpf), eq(Duration.ofSeconds(900)));
    }

    @Test
    @DisplayName("recordFailedAttempt: sets auth:lock:{cpf} when attempts reach 5")
    void shouldLockAccountWhenAttemptsReach5() {
        String cpf = "52998224725";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:failed_attempts:" + cpf)).thenReturn(Mono.just(5L));
        when(redisTemplate.expire(eq("auth:failed_attempts:" + cpf), eq(Duration.ofSeconds(900)))).thenReturn(Mono.just(true));
        when(valueOperations.set(eq("auth:lock:" + cpf), eq("locked"), eq(Duration.ofSeconds(900)))).thenReturn(Mono.just(true));

        long count = adapter.recordFailedAttempt(cpf);

        assertThat(count).isEqualTo(5L);
        verify(valueOperations).set(eq("auth:lock:" + cpf), eq("locked"), eq(Duration.ofSeconds(900)));
    }

    @Test
    @DisplayName("resetFailedAttempts: deletes both failed attempts and lock keys")
    void shouldResetFailedAttemptsAndDeleteKeys() {
        String cpf = "52998224725";
        when(redisTemplate.delete("auth:failed_attempts:" + cpf, "auth:lock:" + cpf)).thenReturn(Mono.just(2L));

        adapter.resetFailedAttempts(cpf);

        verify(redisTemplate).delete("auth:failed_attempts:" + cpf, "auth:lock:" + cpf);
    }

    @Test
    @DisplayName("Graceful fallback when redisTemplate is null")
    void shouldHandleNullRedisTemplateGracefully() {
        RedisAccountLockoutAdapter nullAdapter = new RedisAccountLockoutAdapter(Optional.empty());

        assertThat(nullAdapter.isLocked("52998224725")).isFalse();
        assertThat(nullAdapter.recordFailedAttempt("52998224725")).isEqualTo(0);
        // should not throw:
        nullAdapter.resetFailedAttempts("52998224725");
    }
}
