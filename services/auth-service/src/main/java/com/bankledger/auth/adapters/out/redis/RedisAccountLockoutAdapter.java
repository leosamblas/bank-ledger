package com.bankledger.auth.adapters.out.redis;

import com.bankledger.auth.application.port.out.AccountLockoutPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Reactive, non-blocking Redis implementation of AccountLockoutPort using ReactiveStringRedisTemplate.
 * Provides resilient graceful fallback in case Redis is unavailable or times out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAccountLockoutAdapter implements AccountLockoutPort {

    public static final String LOCK_KEY_PREFIX = "auth:lock:";
    public static final String FAILED_ATTEMPTS_PREFIX = "auth:failed_attempts:";
    public static final Duration LOCKOUT_TTL = Duration.ofSeconds(900); // 15 minutes
    public static final long MAX_FAILED_ATTEMPTS = 5;
    private static final Duration OP_TIMEOUT = Duration.ofMillis(500);

    private final Optional<ReactiveStringRedisTemplate> redisTemplate;

    @Override
    public Mono<Boolean> isLocked(String cpf) {
        if (redisTemplate.isEmpty() || cpf == null || cpf.isBlank()) {
            return Mono.just(false);
        }
        return redisTemplate.get().hasKey(LOCK_KEY_PREFIX + cpf)
                .timeout(OP_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Redis isLocked check failed for CPF {}, falling back gracefully: {}", cpf, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Long> recordFailedAttempt(String cpf) {
        if (redisTemplate.isEmpty() || cpf == null || cpf.isBlank()) {
            return Mono.just(0L);
        }
        String attemptsKey = FAILED_ATTEMPTS_PREFIX + cpf;
        ReactiveStringRedisTemplate redis = redisTemplate.get();

        return redis.opsForValue().increment(attemptsKey)
                .timeout(OP_TIMEOUT)
                .flatMap(count -> {
                    Mono<Boolean> expireMono = (count != null && count == 1)
                            ? redis.expire(attemptsKey, LOCKOUT_TTL).timeout(OP_TIMEOUT).onErrorReturn(false)
                            : Mono.just(true);

                    Mono<Boolean> lockMono = (count != null && count >= MAX_FAILED_ATTEMPTS)
                            ? redis.opsForValue().set(LOCK_KEY_PREFIX + cpf, "locked", LOCKOUT_TTL).timeout(OP_TIMEOUT).onErrorReturn(false)
                            : Mono.just(true);

                    return expireMono.then(lockMono).thenReturn(count != null ? count : 0L);
                })
                .onErrorResume(e -> {
                    log.warn("Redis recordFailedAttempt failed for CPF {}, falling back gracefully: {}", cpf, e.getMessage());
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<Void> resetFailedAttempts(String cpf) {
        if (redisTemplate.isEmpty() || cpf == null || cpf.isBlank()) {
            return Mono.empty();
        }
        return redisTemplate.get().delete(FAILED_ATTEMPTS_PREFIX + cpf, LOCK_KEY_PREFIX + cpf)
                .timeout(OP_TIMEOUT)
                .then()
                .onErrorResume(e -> {
                    log.warn("Redis resetFailedAttempts failed for CPF {}, falling back gracefully: {}", cpf, e.getMessage());
                    return Mono.empty();
                });
    }
}
