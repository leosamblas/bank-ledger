package com.bankledger.ledger.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Security configuration for ledger-service.
 * Hardens internal endpoints requiring internal service key header authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String INTERNAL_SERVICE_KEY_HEADER = "X-Internal-Service-Key";
    public static final String ROLE_INTERNAL_SERVICE = "ROLE_INTERNAL_SERVICE";

    private final String internalServiceKey;

    public SecurityConfig(
            @Value("${app.security.internal-service-key:bankledger-internal-secret-ledger-key-2026}")
            String internalServiceKey
    ) {
        this.internalServiceKey = internalServiceKey;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/internal/v1/ledger/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED,
                                    "Unauthorized: missing or invalid internal service key"
                            );
                            problem.setTitle("Unauthorized");
                            problem.setType(URI.create("about:blank"));
                            problem.setInstance(URI.create(request.getRequestURI()));
                            objectMapper.writeValue(response.getOutputStream(), problem);
                        })
                )
                .addFilterBefore(
                        new InternalServiceKeyFilter(internalServiceKey),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public static ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    public static class InternalServiceKeyFilter extends OncePerRequestFilter {

        private final String expectedKey;

        public InternalServiceKeyFilter(String expectedKey) {
            this.expectedKey = expectedKey;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            String key = request.getHeader(INTERNAL_SERVICE_KEY_HEADER);
            if (expectedKey != null && !expectedKey.isBlank() && expectedKey.equals(key)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "internal-service",
                        null,
                        List.of(new SimpleGrantedAuthority(ROLE_INTERNAL_SERVICE))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                SecurityContextHolder.clearContext();
            }
            filterChain.doFilter(request, response);
        }
    }
}
