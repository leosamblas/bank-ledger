package com.bankledger.account.config;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;

/**
 * Infrastructure security configuration.
 * Configures decentralized RS256 JWT validation using JWKS, public endpoints, and RFC 7807 ProblemDetail entry point.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    public JwksPublicKeyProvider jwksPublicKeyProvider(
            @Value("${app.security.jwks-url:http://localhost:8086/.well-known/jwks.json}") String jwksUrl,
            ObjectMapper objectMapper
    ) {
        return new JwksPublicKeyProvider(jwksUrl, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RsaJwtValidator rsaJwtValidator(JwksPublicKeyProvider keyProvider, ObjectMapper objectMapper) {
        return new RsaJwtValidator(keyProvider, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter(RsaJwtValidator validator) {
        return new JwtAuthenticationTokenFilter(validator);
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
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

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public org.springframework.web.client.RestClient.Builder restClientBuilder() {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(3));
        return org.springframework.web.client.RestClient.builder().requestFactory(requestFactory);
    }
}
