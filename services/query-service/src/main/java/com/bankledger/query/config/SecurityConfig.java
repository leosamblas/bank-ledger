package com.bankledger.query.config;

import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.JwtAuthenticationTokenFilter;
import com.bankledger.commons.security.RsaJwtValidator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;

/**
 * Security configuration for query-service.
 * Provides stateless JWT authentication using decentralized JWKS RS256 token verification.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwksPublicKeyProvider jwksPublicKeyProvider(
            @Value("${app.security.jwks-url:http://localhost:8086/.well-known/jwks.json}") String jwksUrl,
            ObjectMapper objectMapper
    ) {
        return new JwksPublicKeyProvider(jwksUrl, objectMapper);
    }

    @Bean
    public RsaJwtValidator rsaJwtValidator(
            JwksPublicKeyProvider jwksPublicKeyProvider,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        return new RsaJwtValidator(jwksPublicKeyProvider, objectMapper, clock);
    }

    @Bean
    public JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter(RsaJwtValidator rsaJwtValidator) {
        return new JwtAuthenticationTokenFilter(rsaJwtValidator);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationTokenFilter> jwtAuthenticationTokenFilterRegistration(
            JwtAuthenticationTokenFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED,
                                    "Full authentication is required to access this resource"
                            );
                            problem.setTitle("Unauthorized");
                            problem.setType(URI.create("about:blank"));
                            problem.setInstance(URI.create(request.getRequestURI()));
                            objectMapper.writeValue(response.getOutputStream(), problem);
                        })
                )
                .addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public static ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean
    public static Clock clock() {
        return Clock.systemUTC();
    }
}
