package com.bankledger.commons.security;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates RSA 2048 keypairs, signs RS256 JWT tokens with custom claims,
 * and exports public keys in RFC 7517 (JWKS) format.
 */
public class RsaJwtSigner {

    public static final String DEFAULT_ISSUER = "https://auth.bank.internal";
    public static final String DEFAULT_AUDIENCE = "bankledger";
    public static final Duration DEFAULT_VALIDITY = Duration.ofHours(1);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String issuer;
    private final String audience;

    public RsaJwtSigner() {
        this(JsonMapper.builder().build(), Clock.systemUTC(), UUID.randomUUID().toString());
    }

    public RsaJwtSigner(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC(), UUID.randomUUID().toString());
    }

    public RsaJwtSigner(ObjectMapper objectMapper, Clock clock, String keyId) {
        this(generateRsaKeyPair(), keyId, objectMapper, clock, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    public RsaJwtSigner(
            KeyPair keyPair,
            String keyId,
            ObjectMapper objectMapper,
            Clock clock,
            String issuer,
            String audience
    ) {
        Objects.requireNonNull(keyPair, "keyPair must not be null");
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.keyId = (keyId != null && !keyId.isBlank()) ? keyId : UUID.randomUUID().toString();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.issuer = (issuer != null && !issuer.isBlank()) ? issuer : DEFAULT_ISSUER;
        this.audience = (audience != null && !audience.isBlank()) ? audience : DEFAULT_AUDIENCE;
    }

    public static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation algorithm not found", e);
        }
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getKeyId() {
        return keyId;
    }

    /**
     * Signs a JWT with standard {@link TokenClaims} and given validity duration.
     */
    public String generateToken(TokenClaims claims, Duration validity) {
        Map<String, Object> payloadClaims = new LinkedHashMap<>();
        if (claims.cpf() != null) {
            payloadClaims.put("sub", claims.cpf());
            payloadClaims.put("cpf", claims.cpf());
        } else if (claims.customerId() != null) {
            payloadClaims.put("sub", claims.customerId().toString());
        }

        if (claims.customerId() != null) {
            payloadClaims.put("customerId", claims.customerId().toString());
        }
        if (claims.accountId() != null) {
            payloadClaims.put("accountId", claims.accountId().toString());
        }
        if (claims.branch() != null) {
            payloadClaims.put("branch", claims.branch());
        }
        if (claims.accountNumber() != null) {
            payloadClaims.put("accountNumber", claims.accountNumber());
        }
        if (claims.role() != null) {
            payloadClaims.put("role", claims.role());
        }

        return generateToken(payloadClaims, validity);
    }

    public String generateToken(TokenClaims claims) {
        return generateToken(claims, DEFAULT_VALIDITY);
    }

    /**
     * Signs a JWT with custom claims map and given validity duration.
     */
    public String generateToken(Map<String, Object> customClaims, Duration validity) {
        Instant now = Instant.now(clock);
        Duration effectiveValidity = validity != null ? validity : DEFAULT_VALIDITY;
        Instant expiresAt = now.plus(effectiveValidity);

        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");
            header.put("kid", keyId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jti", UUID.randomUUID().toString());
            payload.put("iss", issuer);
            payload.put("aud", audience);
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());

            if (customClaims != null) {
                payload.putAll(customClaims);
            }

            byte[] headerBytes = objectMapper.writeValueAsBytes(header);
            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);

            String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerBytes);
            String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);

            String signingInput = headerB64 + "." + payloadB64;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();

            String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            return signingInput + "." + signatureB64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate and sign RSA JWT", e);
        }
    }

    /**
     * Returns the JWKS representation as a Map adhering to RFC 7517.
     */
    public Map<String, Object> toJwksMap() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", encodeUnsignedBigInt(publicKey.getModulus()));
        jwk.put("e", encodeUnsignedBigInt(publicKey.getPublicExponent()));

        return Map.of("keys", List.of(jwk));
    }

    /**
     * Returns the JWKS representation as JSON adhering to RFC 7517.
     */
    public String toJwksJson() {
        try {
            return objectMapper.writeValueAsString(toJwksMap());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JWKS to JSON", e);
        }
    }

    /**
     * Encodes a BigInteger into unsigned big-endian Base64URL string (RFC 7515 Appendix C).
     */
    public static String encodeUnsignedBigInt(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = 0;
        while (offset < bytes.length - 1 && bytes[offset] == 0) {
            offset++;
        }
        byte[] trimmed = (offset > 0) ? Arrays.copyOfRange(bytes, offset, bytes.length) : bytes;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed);
    }
}
