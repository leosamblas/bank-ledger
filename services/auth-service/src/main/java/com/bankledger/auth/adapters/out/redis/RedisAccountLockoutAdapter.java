package com.bankledger.auth.adapters.out.redis;

import com.bankledger.auth.application.port.out.AccountLockoutPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed implementation of AccountLockoutPort using ReactiveStringRedisTemplate.
 * Provides resilient graceful fallback in case Redis is unavailable or times out.
 */
@Slf4j
@Component
public class RedisAccountLockoutAdapter implements AccountLockoutPort {

    public static final String LOCK_KEY_PREFIX = "auth:lock:";
    public static final String FAILED_ATTEMPTS_PREFIX = "auth:failed_attempts:";
    public static final Duration LOCKOUT_TTL = Duration.ofSeconds(900); // 15 minutes
    public static final long MAX_FAILED_ATTEMPTS = 5;
    private static final Duration OP_TIMEOUT = Duration.ofMillis(500);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisAccountLockoutAdapter(@Autowired(required = false) ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isLocked(String cpf) {
        if (redisTemplate == null || cpf == null || cpf.isBlank()) {
            return false;
        }
        try {
            Boolean hasLock = redisTemplate.hasKey(LOCK_KEY_PREFIX + cpf)
                    .timeout(OP_TIMEOUT)
                    .block();
            return Boolean.TRUE.equals(hasLock);
        } catch (Exception e) {
            log.warn("Redis isLocked check failed for CPF {}, falling back gracefully: {}", cpf, e.getMessage());
            return false;
        }
    }

    @Override
    public long recordFailedAttempt(String cpf) {
        if (redisTemplate == null || cpf == null || cpf.isBlank()) {
            return 0;
        }
        try {
            String attemptsKey = FAILED_ATTEMPTS_PREFIX + cpf;
            Long count = redisTemplate.opsForValue().increment(attemptsKey)
                    .timeout(OP_TIMEOUT)
                    .block();

            if (count != null) {
                // Ensure 15 minute TTL on failed attempts
                redisTemplate.expire(attemptsKey, LOCKOUT_TTL)
                        .timeout(OP_TIMEOUT)
                        .subscribe();

                if (count >= MAX_FAILED_ATTEMPTS) {
                    redisTemplate.opsForValue().set(LOCK_KEY_PREFIX + cpf, "locked", LOCKOUT_TTL)
                            .timeout(OP_TIMEOUT)
                            .block();
                    log.warn("Account locked in Redis for CPF {} after {} failed attempts", cpf, count);
                }
                return count;
            }
            return 0;
        } catch (Exception e) {
            log.warn("Redis recordFailedAttempt failed for CPF {}, falling back gracefully: {}", cpf, e.getMessage());
            return 0;
        }
    }

    @Override
    public void resetFailedAttempts(String cpf) {
        if (redisTemplate == null || cpf == null || cpf.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(FAILED_ATTEMPTS_PREFIX + cpf, LOCK_KEY_PREFIX + cpf)
                    .timeout(OP_TIMEOUT)
                    .subscribe();
        } catch (Exception e) {
            log.warn("Redis resetFailedAttempts failed for CPF {}, falling back gracefully: {}", cpf, e.getMessage());
        }
    }
}
