package com.bankledger.commons.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaJwtSecurityTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);

    private RsaJwtSigner signer;
    private JwksPublicKeyProvider keyProvider;
    private RsaJwtValidator validator;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        signer = new RsaJwtSigner(objectMapper, fixedClock, "bankledger-test-key-1");
        keyProvider = new JwksPublicKeyProvider(objectMapper);
        keyProvider.loadJwks(signer.toJwksJson());
        validator = new RsaJwtValidator(keyProvider, objectMapper, fixedClock);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("RSA Key Generation & JWKS Export")
    class RsaKeyAndJwksTests {

        @Test
        @DisplayName("Should generate valid 2048-bit RSA keypair")
        void shouldGenerateValidRsaKeyPair() {
            RSAPublicKey publicKey = signer.getPublicKey();
            RSAPrivateKey privateKey = signer.getPrivateKey();

            assertThat(publicKey).isNotNull();
            assertThat(privateKey).isNotNull();
            assertThat(publicKey.getAlgorithm()).isEqualTo("RSA");
            assertThat(publicKey.getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
            assertThat(signer.getKeyId()).isEqualTo("bankledger-test-key-1");
        }

        @Test
        @DisplayName("Should export valid RFC 7517 JWKS JSON format")
        void shouldExportValidJwksJson() throws Exception {
            String jwksJson = signer.toJwksJson();
            assertThat(jwksJson).isNotBlank();

            JsonNode root = objectMapper.readTree(jwksJson);
            assertThat(root.has("keys")).isTrue();
            JsonNode keys = root.get("keys");
            assertThat(keys.isArray()).isTrue();
            assertThat(keys.size()).isEqualTo(1);

            JsonNode key = keys.get(0);
            assertThat(key.path("kty").asString("")).isEqualTo("RSA");
            assertThat(key.path("use").asString("")).isEqualTo("sig");
            assertThat(key.path("alg").asString("")).isEqualTo("RS256");
            assertThat(key.path("kid").asString("")).isEqualTo("bankledger-test-key-1");
            assertThat(key.path("n").asString("")).isNotBlank();
            assertThat(key.path("e").asString("")).isNotBlank();
        }

        @Test
        @DisplayName("JwksPublicKeyProvider should parse and cache RSA public key")
        void shouldParseAndCachePublicKey() {
            RSAPublicKey key = keyProvider.getPublicKey("bankledger-test-key-1");
            assertThat(key).isNotNull();
            assertThat(key.getModulus()).isEqualTo(signer.getPublicKey().getModulus());
            assertThat(key.getPublicExponent()).isEqualTo(signer.getPublicKey().getPublicExponent());
        }
    }

    @Nested
    @DisplayName("RS256 Token Signing & Validation")
    class SigningAndValidationTests {

        @Test
        @DisplayName("Should sign RS256 token and successfully extract claims")
        void shouldSignAndValidateToken() {
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();
            String cpf = "12345678901";
            String branch = "0001";
            String accountNumber = "123456-7";
            String role = "ROLE_USER";

            TokenClaims tokenClaims = new TokenClaims(customerId, accountId, cpf, role, branch, accountNumber);
            String token = signer.generateToken(tokenClaims, Duration.ofMinutes(15));

            assertThat(token).isNotBlank();
            String[] parts = token.split("\\.");
            assertThat(parts).hasSize(3);

            TokenClaims parsed = validator.validate(token);
            assertThat(parsed).isNotNull();
            assertThat(parsed.customerId()).isEqualTo(customerId);
            assertThat(parsed.accountId()).isEqualTo(accountId);
            assertThat(parsed.cpf()).isEqualTo(cpf);
            assertThat(parsed.branch()).isEqualTo(branch);
            assertThat(parsed.accountNumber()).isEqualTo(accountNumber);
            assertThat(parsed.role()).isEqualTo(role);
        }

        @Test
        @DisplayName("Should reject token when expired")
        void shouldRejectExpiredToken() {
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();
            TokenClaims tokenClaims = new TokenClaims(customerId, accountId, "12345678901", "ROLE_USER", "0001", "12345-6");

            // Negative validity means already expired
            String token = signer.generateToken(tokenClaims, Duration.ofSeconds(-10));

            assertThatThrownBy(() -> validator.validate(token))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Should reject token with tampered signature")
        void shouldRejectTamperedSignature() {
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();
            TokenClaims tokenClaims = new TokenClaims(customerId, accountId, "12345678901", "ROLE_USER", "0001", "12345-6");
            String token = signer.generateToken(tokenClaims, Duration.ofMinutes(15));

            String[] parts = token.split("\\.");
            // Tamper signature by changing last character
            String tamperedSig = parts[2].substring(0, parts[2].length() - 2) + "xx";
            String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSig;

            assertThatThrownBy(() -> validator.validate(tamperedToken))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        @DisplayName("Should reject token with tampered payload")
        void shouldRejectTamperedPayload() {
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();
            TokenClaims tokenClaims = new TokenClaims(customerId, accountId, "12345678901", "ROLE_USER", "0001", "12345-6");
            String token = signer.generateToken(tokenClaims, Duration.ofMinutes(15));

            String[] parts = token.split("\\.");
            // Replace payload with a different Base64 encoded payload
            String fakePayload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"fake\"}".getBytes());
            String tamperedToken = parts[0] + "." + fakePayload + "." + parts[2];

            assertThatThrownBy(() -> validator.validate(tamperedToken))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("Should reject token with unknown key ID")
        void shouldRejectTokenWithUnknownKeyId() {
            RsaJwtSigner otherSigner = new RsaJwtSigner(objectMapper, fixedClock, "unknown-key-id");
            TokenClaims tokenClaims = new TokenClaims(UUID.randomUUID(), UUID.randomUUID(), "111", "ROLE_USER", "0001", "123");
            String token = otherSigner.generateToken(tokenClaims, Duration.ofMinutes(15));

            assertThatThrownBy(() -> validator.validate(token))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("key");
        }

        @Test
        @DisplayName("Should reject malformed tokens")
        void shouldRejectMalformedTokens() {
            assertThatThrownBy(() -> validator.validate(null))
                    .isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> validator.validate(""))
                    .isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> validator.validate("invalid.token"))
                    .isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> validator.validate("a.b.c"))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("CommonAuthenticatedUser Model")
    class CommonAuthenticatedUserTests {

        @Test
        @DisplayName("Should correctly expose user properties and Spring Security interfaces")
        void shouldExposePropertiesAndInterfaces() {
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();
            String cpf = "12345678901";
            String role = "ROLE_ADMIN";
            String branch = "0001";
            String accountNumber = "99988-7";

            CommonAuthenticatedUser user = new CommonAuthenticatedUser(
                    customerId, accountId, cpf, role, branch, accountNumber
            );

            assertThat(user.customerId()).isEqualTo(customerId);
            assertThat(user.accountId()).isEqualTo(accountId);
            assertThat(user.cpf()).isEqualTo(cpf);
            assertThat(user.role()).isEqualTo(role);
            assertThat(user.branch()).isEqualTo(branch);
            assertThat(user.accountNumber()).isEqualTo(accountNumber);

            // Principal
            assertThat(user.getName()).isEqualTo(customerId.toString());

            // UserDetails
            assertThat(user.getUsername()).isEqualTo(cpf);
            assertThat(user.getPassword()).isNull();
            assertThat(user.getAuthorities()).hasSize(1);
            assertThat(user.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
            assertThat(user.isAccountNonExpired()).isTrue();
            assertThat(user.isAccountNonLocked()).isTrue();
            assertThat(user.isCredentialsNonExpired()).isTrue();
            assertThat(user.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("JwtAuthenticationTokenFilter Integration")
    class JwtAuthenticationTokenFilterTests {

        private JwtAuthenticationTokenFilter filter;

        @BeforeEach
        void setUpFilter() {
            filter = new JwtAuthenticationTokenFilter(validator);
        }

        @Test
        @DisplayName("Should authenticate request with valid Bearer token")
        void shouldAuthenticateWithValidBearerToken() throws Exception {
            UUID customerId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();
            TokenClaims tokenClaims = new TokenClaims(customerId, accountId, "12345678901", "ROLE_USER", "0001", "12345-6");
            String token = signer.generateToken(tokenClaims, Duration.ofMinutes(15));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.isAuthenticated()).isTrue();
            assertThat(auth.getPrincipal()).isInstanceOf(CommonAuthenticatedUser.class);

            CommonAuthenticatedUser principal = (CommonAuthenticatedUser) auth.getPrincipal();
            assertThat(principal.customerId()).isEqualTo(customerId);
            assertThat(principal.accountId()).isEqualTo(accountId);
            assertThat(principal.cpf()).isEqualTo("12345678901");
            assertThat(principal.role()).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("Should leave context empty when Authorization header is missing")
        void shouldLeaveContextEmptyWithoutAuthHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Should leave context empty when Authorization header is not Bearer")
        void shouldLeaveContextEmptyWithNonBearerHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Should clear context when token is invalid")
        void shouldClearContextWhenTokenIsInvalid() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer invalid.token.value");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
