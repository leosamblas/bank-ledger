package com.bankledger.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for auth service security parameters.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class AuthSecurityProperties {

    private String dummyHash = "$2a$10$7EqJtq98hPqEX7fNZaFWoO0Z4qD5gR4m8YQ0CqD8.7Uu7W9n7I.wK";
    private String dummyArgon2Hash = "$2a$10$7EqJtq98hPqEX7fNZaFWoO0Z4qD5gR4m8YQ0CqD8.7Uu7W9n7I.wK";
    private long accessTokenValiditySeconds = 900L;
    private long refreshTokenValiditySeconds = 604800L;
    private String internalServiceKey = "bankledger-internal-secret-ledger-key-2026";

    public String getEffectiveDummyHash() {
        if (dummyHash != null && !dummyHash.isBlank()) {
            return dummyHash;
        }
        if (dummyArgon2Hash != null && !dummyArgon2Hash.isBlank()) {
            return dummyArgon2Hash;
        }
        return "$2a$10$7EqJtq98hPqEX7fNZaFWoO0Z4qD5gR4m8YQ0CqD8.7Uu7W9n7I.wK";
    }
}
