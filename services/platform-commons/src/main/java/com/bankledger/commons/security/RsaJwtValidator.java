package com.bankledger.commons.security;

import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Validates RS256 JWT signatures in-memory using public keys retrieved from {@link JwksPublicKeyProvider},
 * verifies token expiration and claims, and extracts {@link TokenClaims}.
 */
public class RsaJwtValidator {

    private final JwksPublicKeyProvider keyProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RsaJwtValidator(JwksPublicKeyProvider keyProvider) {
        this(keyProvider, JsonMapper.builder().build(), Clock.systemUTC());
    }

    public RsaJwtValidator(JwksPublicKeyProvider keyProvider, ObjectMapper objectMapper) {
        this(keyProvider, objectMapper, Clock.systemUTC());
    }

    public RsaJwtValidator(JwksPublicKeyProvider keyProvider, ObjectMapper objectMapper, Clock clock) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Validates an RS256 JWT and extracts its claims.
     *
     * @param token Compact serialized JWT (header.payload.signature)
     * @return Extracted and verified {@link TokenClaims}
     * @throws BadCredentialsException If token is malformed, expired, has invalid signature, or missing keys
     */
    public TokenClaims validate(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Token must not be null or blank");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new BadCredentialsException("Malformed JWT token structure: expected 3 dot-separated parts");
        }

        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String signatureB64 = parts[2];

        // 1. Decode header and verify alg & kid
        JsonNode headerNode;
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(headerB64);
            headerNode = objectMapper.readTree(headerBytes);
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid token header encoding", e);
        }

        String alg = headerNode.path("alg").asString("");
        if (!"RS256".equalsIgnoreCase(alg)) {
            throw new BadCredentialsException("Unsupported token signing algorithm: " + alg);
        }

        String kid = headerNode.path("kid").asString(null);
        RSAPublicKey publicKey = keyProvider.getPublicKey(kid);
        if (publicKey == null) {
            throw new BadCredentialsException("Public key not found for key ID: " + kid);
        }

        // 2. Verify signature
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getUrlDecoder().decode(signatureB64);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid token signature encoding", e);
        }

        String signingInput = headerB64 + "." + payloadB64;
        try {
            Signature sigVerifier = Signature.getInstance("SHA256withRSA");
            sigVerifier.initVerify(publicKey);
            sigVerifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!sigVerifier.verify(signatureBytes)) {
                throw new BadCredentialsException("Invalid token signature");
            }
        } catch (BadCredentialsException bce) {
            throw bce;
        } catch (Exception e) {
            throw new BadCredentialsException("Failed to verify token signature", e);
        }

        // 3. Decode and validate payload claims
        JsonNode payloadNode;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            payloadNode = objectMapper.readTree(payloadBytes);
        } catch (Exception e) {
            throw new BadCredentialsException("Failed to decode token payload", e);
        }

        if (!payloadNode.has("exp") || payloadNode.path("exp").isNull()) {
            throw new BadCredentialsException("Token missing required exp claim");
        }
        long exp = payloadNode.path("exp").asLong(0);
        long now = Instant.now(clock).getEpochSecond();
        if (exp <= 0 || now > exp) {
            throw new BadCredentialsException("Token has expired");
        }

        if (payloadNode.has("nbf") && !payloadNode.path("nbf").isNull()) {
            long nbf = payloadNode.path("nbf").asLong(0);
            if (now < nbf) {
                throw new BadCredentialsException("Token is not yet valid");
            }
        }

        UUID customerId = null;
        if (payloadNode.has("customerId")) {
            try {
                customerId = UUID.fromString(payloadNode.path("customerId").asString(""));
            } catch (Exception ignored) {
            }
        }

        UUID accountId = null;
        if (payloadNode.has("accountId")) {
            try {
                accountId = UUID.fromString(payloadNode.path("accountId").asString(""));
            } catch (Exception ignored) {
            }
        }

        String cpf = payloadNode.has("cpf") ? payloadNode.path("cpf").asString("") : payloadNode.path("sub").asString(null);
        String role = payloadNode.path("role").asString("ROLE_USER");
        String branch = payloadNode.path("branch").asString("0001");
        String accountNumber = payloadNode.path("accountNumber").asString(null);

        return new TokenClaims(customerId, accountId, cpf, role, branch, accountNumber);
    }

    /**
     * Alias for {@link #validate(String)} for compatibility.
     */
    public TokenClaims validateToken(String token) {
        return validate(token);
    }
}
