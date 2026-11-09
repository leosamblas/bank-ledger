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
    @DisplayName("generateToken() produz JWT válido com 3 partes e claims decodificáveis")
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
    @DisplayName("parseToken() falha com BadCredentialsException quando token está expirado")
    void shouldFailWhenTokenIsExpired() {
        Clock pastClock = Clock.fixed(FIXED_NOW.minusSeconds(7200), ZoneOffset.UTC);
        JwtTokenAdapter pastAdapter = new JwtTokenAdapter(JsonMapper.builder().build(), pastClock, SECRET, 3600L);

        String expiredToken = pastAdapter.generateToken(customer, account);

        assertThatThrownBy(() -> adapter.parseToken(expiredToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("parseToken() falha quando a assinatura é adulterada")
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
    @DisplayName("parseToken() falha quando o token é nulo, em branco ou malformado")
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
}

