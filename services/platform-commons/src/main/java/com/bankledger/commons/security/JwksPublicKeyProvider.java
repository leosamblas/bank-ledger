package com.bankledger.commons.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parses and caches RSA public keys by Key ID (kid) conforming to RFC 7517 (JWKS).
 * Supports loading from static JWKS JSON strings and fetching / refreshing from HTTP JWKS endpoints.
 */
public class JwksPublicKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwksPublicKeyProvider.class);
    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REFRESH_COOLDOWN = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final String jwksUrl;
    private final HttpClient httpClient;
    private final Duration refreshCooldown;
    private final AtomicReference<Instant> lastRefreshTime = new AtomicReference<>(Instant.EPOCH);
    private final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    public JwksPublicKeyProvider() {
        this(null, JsonMapper.builder().build());
    }

    public JwksPublicKeyProvider(ObjectMapper objectMapper) {
        this(null, objectMapper);
    }

    public JwksPublicKeyProvider(String jwksUrl, ObjectMapper objectMapper) {
        this(jwksUrl, objectMapper, HttpClient.newBuilder().connectTimeout(DEFAULT_HTTP_TIMEOUT).build(), DEFAULT_REFRESH_COOLDOWN);
    }

    public JwksPublicKeyProvider(String jwksUrl, ObjectMapper objectMapper, HttpClient httpClient) {
        this(jwksUrl, objectMapper, httpClient, DEFAULT_REFRESH_COOLDOWN);
    }

    public JwksPublicKeyProvider(String jwksUrl, ObjectMapper objectMapper, HttpClient httpClient, Duration refreshCooldown) {
        this.jwksUrl = jwksUrl;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.refreshCooldown = refreshCooldown != null ? refreshCooldown : DEFAULT_REFRESH_COOLDOWN;
    }

    /**
     * Retrieves an RSA public key by its key ID.
     * If the key is not found in cache and a JWKS URL is configured, triggers a refresh.
     *
     * @param kid Key ID to look up
     * @return RSAPublicKey, or null if not found
     */
    public RSAPublicKey getPublicKey(String kid) {
        if (kid != null && keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        if (jwksUrl != null && !jwksUrl.isBlank()) {
            refreshFromUrl();
            if (kid != null && keyCache.containsKey(kid)) {
                return keyCache.get(kid);
            }
        }

        if (kid == null && keyCache.size() == 1) {
            return keyCache.values().iterator().next();
        }

        return kid != null ? keyCache.get(kid) : null;
    }

    /**
     * Stores an RSA public key directly in the cache.
     *
     * @param kid       Key identifier
     * @param publicKey RSA public key
     */
    public void putKey(String kid, RSAPublicKey publicKey) {
        Objects.requireNonNull(kid, "kid must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        keyCache.put(kid, publicKey);
    }

    /**
     * Parses JWKS JSON format and stores all RSA keys in memory.
     *
     * @param jwksJson JSON string adhering to RFC 7517
     */
    public void loadJwks(String jwksJson) {
        if (jwksJson == null || jwksJson.isBlank()) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(jwksJson);
            JsonNode keysNode = root.path("keys");
            if (!keysNode.isArray()) {
                return;
            }

            for (JsonNode keyNode : keysNode) {
                String kty = keyNode.path("kty").asString("");
                if (!"RSA".equalsIgnoreCase(kty)) {
                    continue;
                }

                String kid = keyNode.path("kid").asString("");
                String n = keyNode.path("n").asString("");
                String e = keyNode.path("e").asString("");

                if (!n.isBlank() && !e.isBlank()) {
                    BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
                    BigInteger publicExponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));

                    RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, publicExponent);
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(spec);

                    if (!kid.isBlank()) {
                        keyCache.put(kid, publicKey);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to parse JWKS JSON", ex);
            throw new IllegalArgumentException("Invalid JWKS JSON structure", ex);
        }
    }

    /**
     * Refreshes public keys from the configured remote JWKS endpoint if cooldown elapsed.
     */
    public synchronized void refreshFromUrl() {
        if (jwksUrl == null || jwksUrl.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        Instant last = lastRefreshTime.get();
        if (Duration.between(last, now).compareTo(refreshCooldown) < 0) {
            log.debug("Skipping JWKS refresh: throttled by cooldown of {}", refreshCooldown);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUrl))
                    .timeout(DEFAULT_HTTP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            lastRefreshTime.set(Instant.now());
            if (response.statusCode() == 200) {
                loadJwks(response.body());
                log.info("Successfully refreshed JWKS keys from {}", jwksUrl);
            } else {
                log.warn("Failed to fetch JWKS from {}: HTTP {}", jwksUrl, response.statusCode());
            }
        } catch (Exception ex) {
            lastRefreshTime.set(Instant.now());
            log.warn("Error refreshing JWKS keys from {}: {}", jwksUrl, ex.getMessage());
        }
    }

    public Map<String, RSAPublicKey> getCachedKeys() {
        return Map.copyOf(keyCache);
    }
}
