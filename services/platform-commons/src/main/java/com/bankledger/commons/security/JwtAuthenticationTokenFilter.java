package com.bankledger.commons.security;

import com.bankledger.commons.constant.HttpHeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Standard HTTP servlet filter that intercepts incoming requests, extracts the Bearer token
 * from the {@code Authorization} header, validates RS256 signature and claims using {@link RsaJwtValidator},
 * and populates the {@link SecurityContextHolder} with a {@link CommonAuthenticatedUser} principal.
 */
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private final RsaJwtValidator validator;

    public JwtAuthenticationTokenFilter(RsaJwtValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

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
                    TokenClaims claims = validator.validate(token);
                    CommonAuthenticatedUser principal = new CommonAuthenticatedUser(
                            claims.customerId(),
                            claims.accountId(),
                            claims.cpf(),
                            claims.role(),
                            claims.branch(),
                            claims.accountNumber()
                    );

                    List<SimpleGrantedAuthority> authorities = (claims.role() != null && !claims.role().isBlank())
                            ? List.of(new SimpleGrantedAuthority(claims.role()))
                            : List.of(new SimpleGrantedAuthority("ROLE_USER"));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, token, authorities);
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
