package com.bankledger.account.config;

import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.commons.security.CommonAuthenticatedUser;
import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.JwtAuthenticationTokenFilter;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
import com.bankledger.commons.security.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    private RsaJwtSigner rsaJwtSigner;
    private JwksPublicKeyProvider jwksPublicKeyProvider;
    private RsaJwtValidator rsaJwtValidator;
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        rsaJwtSigner = new RsaJwtSigner();
        jwksPublicKeyProvider = new JwksPublicKeyProvider();
        jwksPublicKeyProvider.putKey(rsaJwtSigner.getKeyId(), rsaJwtSigner.getPublicKey());
        rsaJwtValidator = new RsaJwtValidator(jwksPublicKeyProvider);
        jwtAuthenticationTokenFilter = new JwtAuthenticationTokenFilter(rsaJwtValidator);
    }

    @Test
    @DisplayName("JwtAuthenticationTokenFilter authenticates and populates SecurityContext when RS256 token is valid")
    void shouldAuthenticateValidBearerToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TokenClaims claims = new TokenClaims(customerId, accountId, "12345678909", "ROLE_USER", "0001", "123456");
        String token = rsaJwtSigner.generateToken(claims);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/me");
        request.addHeader(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtAuthenticationTokenFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        CommonAuthenticatedUser principal = (CommonAuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.customerId()).isEqualTo(customerId);
        assertThat(principal.accountId()).isEqualTo(accountId);
        assertThat(principal.role()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("JwtAuthenticationTokenFilter clears SecurityContext when token is invalid or has bad signature")
    void shouldClearContextWhenTokenIsInvalid() throws Exception {
        RsaJwtSigner untrustedSigner = new RsaJwtSigner();
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TokenClaims claims = new TokenClaims(customerId, accountId, "12345678909", "ROLE_USER", "0001", "123456");
        String untrustedToken = untrustedSigner.generateToken(claims);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/me");
        request.addHeader(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + untrustedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtAuthenticationTokenFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
