package com.bankledger.account.config;

import com.bankledger.account.adapters.in.rest.AuthenticatedUser;
import com.bankledger.account.application.port.out.TokenClaims;
import com.bankledger.account.application.port.out.TokenServicePort;
import com.bankledger.commons.constant.HttpHeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenServicePort tokenServicePort;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaderConstants.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(HttpHeaderConstants.BEARER_PREFIX)) {
            String token = authHeader.substring(HttpHeaderConstants.BEARER_PREFIX.length()).trim();
            if (!token.isBlank()) {
                try {
                    TokenClaims claims = tokenServicePort.parseToken(token);
                    AuthenticatedUser principal = new AuthenticatedUser(
                            claims.customerId(),
                            claims.accountId(),
                            claims.branch(),
                            claims.accountNumber(),
                            claims.role()
                    );

                    List<SimpleGrantedAuthority> authorities = (claims.role() != null && !claims.role().isBlank())
                            ? List.of(new SimpleGrantedAuthority(claims.role()))
                            : List.of(new SimpleGrantedAuthority("ROLE_USER"));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception ex) {
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
