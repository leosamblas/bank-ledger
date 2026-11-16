package com.bankledger.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import tools.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import java.net.URI;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ObjectMapper objectMapper
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login", "/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/auth/refresh").permitAll()
                        .pathMatchers(HttpMethod.GET, "/.well-known/jwks.json", "/api/v1/auth/.well-known/jwks.json").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/introspect").permitAll()
                        .pathMatchers("/internal/v1/auth/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, authException) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED,
                                    "Full authentication is required to access this resource"
                            );
                            problem.setTitle("Unauthorized");
                            problem.setType(URI.create("about:blank"));
                            problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
                            try {
                                byte[] bytes = objectMapper.writeValueAsBytes(problem);
                                DataBuffer buffer = response.bufferFactory().wrap(bytes);
                                return response.writeWith(Mono.just(buffer));
                            } catch (Exception e) {
                                return Mono.error(e);
                            }
                        })
                )
                .build();
    }
}
