package com.bankledger.notification.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Properties;

/**
 * Mail configuration active ONLY when the feature flag app.mail.enabled is true.
 * Prevents instantiation of JavaMailSender and Actuator MailHealthIndicator when mail sending is disabled.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {

    @Bean
    public JavaMailSender javaMailSender(MailProperties properties, ObjectProvider<SslBundles> sslBundles) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        applyProperties(properties, sender, sslBundles.getIfAvailable());
        return sender;
    }

    private void applyProperties(MailProperties properties, JavaMailSenderImpl sender, SslBundles sslBundles) {
        if (StringUtils.hasLength(properties.getHost())) {
            sender.setHost(properties.getHost());
        }
        if (properties.getPort() != null) {
            sender.setPort(properties.getPort());
        }
        if (StringUtils.hasLength(properties.getUsername())) {
            sender.setUsername(properties.getUsername());
        }
        if (StringUtils.hasLength(properties.getPassword())) {
            sender.setPassword(properties.getPassword());
        }
        if (StringUtils.hasLength(properties.getProtocol())) {
            sender.setProtocol(properties.getProtocol());
        }
        if (properties.getDefaultEncoding() != null) {
            sender.setDefaultEncoding(properties.getDefaultEncoding().name());
        }

        Properties javaMailProperties = asProperties(properties.getProperties());
        String protocol = properties.getProtocol();
        protocol = (!StringUtils.hasLength(protocol)) ? "smtp" : protocol;

        MailProperties.Ssl ssl = properties.getSsl();
        if (ssl != null && (ssl.isEnabled() || StringUtils.hasLength(ssl.getBundle()))) {
            if (ssl.isVerifyHostname()) {
                javaMailProperties.setProperty("mail." + protocol + ".ssl.checkserveridentity", "true");
            }
            if (ssl.isEnabled()) {
                javaMailProperties.setProperty("mail." + protocol + ".ssl.enable", "true");
            }
            if (StringUtils.hasLength(ssl.getBundle())) {
                Assert.state(sslBundles != null, "'sslBundles' must not be null");
                SslBundle sslBundle = sslBundles.getBundle(ssl.getBundle());
                javaMailProperties.put("mail." + protocol + ".ssl.socketFactory",
                        sslBundle.createSslContext().getSocketFactory());
            }
        }

        if (!javaMailProperties.isEmpty()) {
            sender.setJavaMailProperties(javaMailProperties);
        }
    }

    private Properties asProperties(Map<String, String> source) {
        Properties properties = new Properties();
        if (source != null) {
            properties.putAll(source);
        }
        return properties;
    }
}
