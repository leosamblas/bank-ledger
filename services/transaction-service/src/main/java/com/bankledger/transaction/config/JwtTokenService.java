package com.bankledger.transaction.config;

import com.bankledger.transaction.adapters.in.rest.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class JwtTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String DEFAULT_SECRET = "bankledger-super-secure-secret-key-that-is-at-least-256-bits-long";
    private static final long DEFAULT_EXPIRATION_SECONDS = 3600L;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secretKeyBytes;
    private final long expirationSeconds;

    public JwtTokenService(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${bankledger.security.jwt.secret:" + DEFAULT_SECRET + "}") String secret,
            @Value("${bankledger.security.jwt.expiration-seconds:" + DEFAULT_EXPIRATION_SECONDS + "}") long expirationSeconds
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        String effectiveSecret = (secret != null && !secret.isBlank()) ? secret : DEFAULT_SECRET;
        this.secretKeyBytes = effectiveSecret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds > 0 ? expirationSeconds : DEFAULT_EXPIRATION_SECONDS;
    }

    public String generateToken(UUID customerId, UUID accountId) {
        return generateToken(customerId, accountId, "ROLE_USER");
    }

    public String generateToken(UUID customerId, UUID accountId, String role) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        try {
            Map<String, Object> header = Map.of(
                    "alg", "HS256",
                    "typ", "JWT"
            );
            byte[] headerBytes = objectMapper.writeValueAsBytes(header);
            String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerBytes);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", customerId.toString());
            payload.put("customerId", customerId.toString());
            payload.put("accountId", accountId.toString());
            payload.put("role", role != null ? role : "ROLE_USER");
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());

            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
            String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);

            String signingInput = headerB64 + "." + payloadB64;
            byte[] signature = sign(signingInput.getBytes(StandardCharsets.UTF_8));
            String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            return signingInput + "." + signatureB64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT token", e);
        }
    }

    public AuthenticatedUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Token must not be null or blank");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new BadCredentialsException("Malformed JWT token structure");
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSig = sign(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] actualSig;
        try {
            actualSig = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid token signature encoding", e);
        }

        if (!MessageDigest.isEqual(expectedSig, actualSig)) {
            throw new BadCredentialsException("Invalid JWT signature");
        }

        JsonNode payloadNode;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            payloadNode = objectMapper.readTree(payloadBytes);
        } catch (Exception e) {
            throw new BadCredentialsException("Failed to decode token payload", e);
        }

        if (!payloadNode.has("exp") || payloadNode.path("exp").isNull()) {
            throw new BadCredentialsException("Token is missing required exp claim");
        }
        long exp = payloadNode.path("exp").asLong(0);
        if (exp <= 0) {
            throw new BadCredentialsException("Token exp claim must be positive");
        }
        Instant now = Instant.now(clock);
        if (now.getEpochSecond() > exp) {
            throw new BadCredentialsException("Token has expired");
        }

        UUID customerId = null;
        if (payloadNode.has("customerId") && !payloadNode.path("customerId").asString("").isBlank()) {
            try {
                customerId = UUID.fromString(payloadNode.path("customerId").asString());
            } catch (IllegalArgumentException ignored) {}
        }
        if (customerId == null && payloadNode.has("sub") && !payloadNode.path("sub").asString("").isBlank()) {
            try {
                customerId = UUID.fromString(payloadNode.path("sub").asString());
            } catch (IllegalArgumentException ignored) {}
        }
        if (customerId == null) {
            throw new BadCredentialsException("Token is missing valid customerId or UUID sub claim");
        }

        UUID accountId;
        try {
            accountId = UUID.fromString(payloadNode.path("accountId").asString());
        } catch (Exception e) {
            throw new BadCredentialsException("Token is missing required accountId UUID claim", e);
        }

        String role = payloadNode.has("role") ? payloadNode.path("role").asString("ROLE_USER") : "ROLE_USER";

        return new AuthenticatedUser(customerId, accountId, role);
    }

    private byte[] sign(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}
