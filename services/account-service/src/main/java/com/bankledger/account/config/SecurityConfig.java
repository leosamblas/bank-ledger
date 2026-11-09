package com.bankledger.account.config;

import com.bankledger.account.application.port.out.PasswordEncoderPort;
import com.bankledger.account.application.port.out.TokenClaims;
import com.bankledger.account.application.port.out.TokenServicePort;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Infrastructure configuration class.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoderPort passwordEncoderPort() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return new PasswordEncoderPort() {
            @Override
            public String encode(CharSequence rawPassword) {
                return encoder.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encoder.matches(rawPassword, encodedPassword);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenServicePort tokenServicePort() {
        return new TokenServicePort() {
            @Override
            public String generateToken(Customer customer, Account account) {
                return "stub-token-" + customer.getId();
            }

            @Override
            public TokenClaims parseToken(String token) {
                throw new UnsupportedOperationException("Token parsing not implemented in stub");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
