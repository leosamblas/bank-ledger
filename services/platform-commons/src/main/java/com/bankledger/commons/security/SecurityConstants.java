package com.bankledger.commons.security;

import java.time.Duration;

/**
 * Standard security constants, header names, roles, JWT claims, endpoints, and reactive retry policies.
 */
public final class SecurityConstants {

    public static final String INTERNAL_SERVICE_KEY_HEADER = "X-Internal-Service-Key";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_CLIENT = "ROLE_CLIENT";

    public static final String CLAIM_SUB = "sub";
    public static final String CLAIM_CPF = "cpf";
    public static final String CLAIM_CUSTOMER_ID = "customerId";
    public static final String CLAIM_ACCOUNT_ID = "accountId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_BRANCH = "branch";
    public static final String CLAIM_ACCOUNT_NUMBER = "accountNumber";
    public static final String CLAIM_JTI = "jti";
    public static final String CLAIM_EXP = "exp";
    public static final String CLAIM_IAT = "iat";
    public static final String CLAIM_ISS = "iss";
    public static final String CLAIM_AUD = "aud";

    public static final String JWKS_WELL_KNOWN_PATH = "/.well-known/jwks.json";
    public static final String AUTH_LOGIN_PATH = "/api/v1/auth/login";
    public static final String AUTH_REFRESH_PATH = "/api/v1/auth/refresh";
    public static final String AUTH_INTROSPECT_PATH = "/api/v1/auth/introspect";
    public static final String INTERNAL_AUTH_CREDENTIALS_PATH = "/internal/v1/auth/credentials";
    public static final String INTERNAL_LEDGER_PATH_PATTERN = "/internal/v1/ledger/**";

    public static final int DEFAULT_RETRY_ATTEMPTS = 3;
    public static final long DEFAULT_RETRY_DELAY_MS = 50L;
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(DEFAULT_RETRY_DELAY_MS);

    private SecurityConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
