package com.bankledger.auth.config;

import com.bankledger.commons.security.JwksPublicKeyProvider;
import com.bankledger.commons.security.RsaJwtSigner;
import com.bankledger.commons.security.RsaJwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPair;
import java.time.Clock;
import java.util.UUID;

@Configuration
public class JwtSignerConfig {

    @Value("${bankledger.security.jwt.issuer:https://auth.bank.internal}")
    private String issuer;

    @Value("${bankledger.security.jwt.audience:bankledger}")
    private String audience;

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

    @Bean
    public RsaJwtSigner rsaJwtSigner(ObjectMapper objectMapper, Clock clock) {
        KeyPair keyPair = RsaJwtSigner.generateRsaKeyPair();
        String keyId = UUID.randomUUID().toString();
        return new RsaJwtSigner(keyPair, keyId, objectMapper, clock, issuer, audience);
    }

    @Bean
    public RsaJwtValidator rsaJwtValidator(RsaJwtSigner rsaJwtSigner, ObjectMapper objectMapper, Clock clock) {
        JwksPublicKeyProvider keyProvider = new JwksPublicKeyProvider(objectMapper);
        keyProvider.putKey(rsaJwtSigner.getKeyId(), rsaJwtSigner.getPublicKey());
        return new RsaJwtValidator(keyProvider, objectMapper, clock);
    }
}
