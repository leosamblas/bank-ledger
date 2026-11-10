package com.bankledger.account.config;

import com.bankledger.account.adapters.in.rest.AuthenticatedUser;
import com.bankledger.account.application.port.out.TokenClaims;
import com.bankledger.account.application.port.out.TokenServicePort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private TokenServicePort tokenServicePort;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(tokenServicePort);
    }

    @Test
    @DisplayName("JwtAuthenticationFilter authenticates and populates SecurityContext when token is valid")
    void shouldAuthenticateValidBearerToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TokenClaims claims = new TokenClaims(customerId, accountId, "0001", "123456", "ROLE_USER");
        when(tokenServicePort.parseToken("valid-token")).thenReturn(claims);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.customerId()).isEqualTo(customerId);
        assertThat(principal.accountId()).isEqualTo(accountId);
    }

    @Test
    @DisplayName("JwtAuthenticationFilter clears SecurityContext when token is invalid")
    void shouldClearContextWhenTokenIsInvalid() throws Exception {
        when(tokenServicePort.parseToken("invalid-token")).thenThrow(new RuntimeException("Bad signature"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

