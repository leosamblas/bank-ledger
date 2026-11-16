package com.bankledger.auth.application.port.out;

/**
 * Port for managing fast account lockout and failed login attempts in Redis.
 */
public interface AccountLockoutPort {

    /**
     * Checks if the given CPF is locked out in Redis.
     *
     * @param cpf sanitized 11-digit CPF
     * @return true if account is locked in Redis, false otherwise
     */
    boolean isLocked(String cpf);

    /**
     * Records a failed login attempt in Redis.
     * Increments failed attempts with TTL 900s. If attempts >= 5, locks account for 900s.
     *
     * @param cpf sanitized 11-digit CPF
     * @return the current count of failed attempts
     */
    long recordFailedAttempt(String cpf);

    /**
     * Resets failed login attempts and unlocks account in Redis on successful login.
     *
     * @param cpf sanitized 11-digit CPF
     */
    void resetFailedAttempts(String cpf);
}
