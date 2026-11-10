package com.bankledger.account.adapters.out.security;

import com.bankledger.account.application.port.out.TokenClaims;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.account.domain.vo.AccountNumber;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenAdapterTest {

    private static final String SECRET = "01234567890123456789012345678901"; // 32 bytes (256 bits)
    private static final Instant FIXED_NOW = Instant.parse("2026-11-09T20:00:00Z");

    private JwtTokenAdapter adapter;
    private Customer customer;
    private Account account;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        adapter = new JwtTokenAdapter(JsonMapper.builder().build(), fixedClock, SECRET, 3600L);

        UUID customerId = UUID.randomUUID();
        customer = Customer.create(
                customerId,
                "Carlos Eduardo Silva",
                Cpf.of("52998224725"),
                Email.of("carlos@example.com"),
                Phone.of("11987654321"),
                LocalDate.of(1990, 5, 15),
                "hashedPass",
                FIXED_NOW
        );

        UUID accountId = UUID.randomUUID();
        AccountNumber accountNumber = AccountNumber.ofBranchAndNumber("0001", "123456");
        account = Account.create(
                accountId,
                customerId,
                "0001",
                accountNumber,
                FIXED_NOW
        );
    }

    @Test
    @DisplayName("generateToken() produces valid 3-part JWT with decodable claims")
    void shouldGenerateAndParseTokenSuccessfully() {
        String token = adapter.generateToken(customer, account);

        assertThat(token).isNotBlank();
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        TokenClaims claims = adapter.parseToken(token);
        assertThat(claims.customerId()).isEqualTo(customer.getId());
        assertThat(claims.accountId()).isEqualTo(account.getId());
        assertThat(claims.branch()).isEqualTo("0001");
        assertThat(claims.accountNumber()).isEqualTo("123456");
        assertThat(claims.role()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("parseToken() fails with BadCredentialsException when token is expired")
    void shouldFailWhenTokenIsExpired() {
        Clock pastClock = Clock.fixed(FIXED_NOW.minusSeconds(7200), ZoneOffset.UTC);
        JwtTokenAdapter pastAdapter = new JwtTokenAdapter(JsonMapper.builder().build(), pastClock, SECRET, 3600L);

        String expiredToken = pastAdapter.generateToken(customer, account);

        assertThatThrownBy(() -> adapter.parseToken(expiredToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("parseToken() fails when signature is tampered")
    void shouldFailWhenSignatureIsTampered() {
        String token = adapter.generateToken(customer, account);
        String[] parts = token.split("\\.");

        // Tamper signature by modifying last characters
        String tamperedToken = parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 2) + "==";

        assertThatThrownBy(() -> adapter.parseToken(tamperedToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("parseToken() fails when token is null, blank, or malformed")
    void shouldFailWhenTokenIsMalformed() {
        assertThatThrownBy(() -> adapter.parseToken(null))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> adapter.parseToken("   "))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> adapter.parseToken("only.two.parts.extra"))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> adapter.parseToken("invalidToken"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("parseToken() fails when token is missing required exp claim")
    void shouldFailWhenTokenIsMissingExpClaim() throws Exception {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payloadWithoutExp = String.format(
                "{\"customerId\":\"%s\",\"accountId\":\"%s\",\"branch\":\"0001\",\"accountNumber\":\"123456\",\"role\":\"ROLE_USER\"}",
                customer.getId(), account.getId()
        );
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadWithoutExp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(signingInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sigB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sig);

        String tokenWithoutExp = signingInput + "." + sigB64;

        assertThatThrownBy(() -> adapter.parseToken(tokenWithoutExp))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("exp");
    }
}

