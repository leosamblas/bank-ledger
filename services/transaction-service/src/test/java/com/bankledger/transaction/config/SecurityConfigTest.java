package com.bankledger.transaction.config;

import com.bankledger.commons.constant.HttpHeaderConstants;
import com.bankledger.commons.security.CommonAuthenticatedUser;
import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.JwtAuthenticationTokenFilter;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
import com.bankledger.commons.security.SecurityConstants;
import com.bankledger.commons.security.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityConfig and JwtAuthenticationTokenFilter Unit Tests")
class SecurityConfigTest {

    private RsaJwtSigner rsaJwtSigner;
    private JwksPublicKeyProvider jwksPublicKeyProvider;
    private RsaJwtValidator rsaJwtValidator;
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;
    private SecurityConfig securityConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        rsaJwtSigner = new RsaJwtSigner();
        jwksPublicKeyProvider = new JwksPublicKeyProvider();
        jwksPublicKeyProvider.putKey(rsaJwtSigner.getKeyId(), rsaJwtSigner.getPublicKey());
        objectMapper = JsonMapper.builder().build();
        securityConfig = new SecurityConfig();
        rsaJwtValidator = securityConfig.rsaJwtValidator(jwksPublicKeyProvider, objectMapper, Clock.systemUTC());
        jwtAuthenticationTokenFilter = securityConfig.jwtAuthenticationTokenFilter(rsaJwtValidator);
    }

    @Test
    @DisplayName("JwtAuthenticationTokenFilter authenticates and populates SecurityContext when RS256 token is valid")
    void shouldAuthenticateValidBearerToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TokenClaims claims = new TokenClaims(customerId, accountId, "12345678909", SecurityConstants.ROLE_USER, "0001", "123456");
        String token = rsaJwtSigner.generateToken(claims);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions/" + UUID.randomUUID());
        request.addHeader(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtAuthenticationTokenFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        CommonAuthenticatedUser principal = (CommonAuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.customerId()).isEqualTo(customerId);
        assertThat(principal.accountId()).isEqualTo(accountId);
        assertThat(principal.role()).isEqualTo(SecurityConstants.ROLE_USER);
    }

    @Test
    @DisplayName("JwtAuthenticationTokenFilter clears SecurityContext when token is invalid or has untrusted signature")
    void shouldClearContextWhenTokenIsInvalid() throws Exception {
        RsaJwtSigner untrustedSigner = new RsaJwtSigner();
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TokenClaims claims = new TokenClaims(customerId, accountId, "12345678909", SecurityConstants.ROLE_USER, "0001", "123456");
        String untrustedToken = untrustedSigner.generateToken(claims);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions/" + UUID.randomUUID());
        request.addHeader(HttpHeaderConstants.AUTHORIZATION, HttpHeaderConstants.BEARER_PREFIX + untrustedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtAuthenticationTokenFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("FilterRegistrationBean disables duplicate servlet registration for JwtAuthenticationTokenFilter")
    void shouldDisableServletRegistration() {
        FilterRegistrationBean<JwtAuthenticationTokenFilter> registration =
                securityConfig.jwtAuthenticationFilterRegistration(jwtAuthenticationTokenFilter);
        assertThat(registration).isNotNull();
        assertThat(registration.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("SecurityConfig beans instantiation and defaults verification")
    void shouldInstantiateConfigBeans() {
        JwksPublicKeyProvider keyProvider = securityConfig.jwksPublicKeyProvider("http://localhost:8086/.well-known/jwks.json", objectMapper);
        assertThat(keyProvider).isNotNull();

        ObjectMapper mapper = SecurityConfig.objectMapper();
        assertThat(mapper).isNotNull();

        Clock clock = SecurityConfig.clock();
        assertThat(clock).isNotNull();
    }
}
