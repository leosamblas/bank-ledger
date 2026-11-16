package com.bankledger.notification.application.service;

import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Objects;

@Slf4j
@Service
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String fromAddress;
    private final String loginUrl;
    private final String emailTemplate;

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${spring.mail.username:noreply@bankledger.com}") String fromAddress
    ) {
        this(mailSender, mailEnabled, fromAddress, "http://localhost:3000/login");
    }

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${spring.mail.username:noreply@bankledger.com}") String fromAddress,
            @Value("${app.mail.login-url:http://localhost:3000/login}") String loginUrl
    ) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.fromAddress = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : "noreply@bankledger.com";
        this.loginUrl = (loginUrl != null && !loginUrl.isBlank()) ? loginUrl : "http://localhost:3000/login";
        this.emailTemplate = loadTemplate("templates/account-ready-email.html");
    }

    public Mono<Void> sendAccountReadyEmail(AccountReadyPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");

        if (!mailEnabled) {
            log.info("Email sending is DISABLED (feature flag app.mail.enabled=false). Skipping SMTP for customer: {} (email: {})",
                    payload.customerId(), payload.email());
            return Mono.empty();
        }

        return Mono.<Void>fromRunnable(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(
                        mimeMessage,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                        StandardCharsets.UTF_8.name()
                );

                helper.setFrom(fromAddress);
                helper.setTo(payload.email());
                helper.setSubject("Bem-vindo ao Nexus Bank! Sua conta está pronta para uso");

                String plainText = buildPlainText(payload);
                String htmlText = renderHtml(payload);

                if (htmlText != null) {
                    helper.setText(plainText, htmlText);
                } else {
                    helper.setText(plainText);
                }

                log.debug("Dispatching account ready email to {} from {}", payload.email(), fromAddress);
                mailSender.send(mimeMessage);
                log.info("Successfully sent account ready email to: {} for customer: {}", payload.email(), payload.customerId());
            } catch (MessagingException e) {
                log.error("Failed to prepare MimeMessage for customer: {}", payload.customerId(), e);
                throw new MailPreparationException("Failed to prepare email message", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    String buildPlainText(AccountReadyPayload payload) {
        return String.format(
                "Olá %s,%n%nSua conta no Nexus Bank está pronta para uso!%nAgência: %s%nConta: %s%n%nAcesse sua conta em: %s%n%nObrigado por escolher o Nexus Bank.",
                payload.fullName(), payload.branch(), payload.accountNumber(), loginUrl
        );
    }

    String renderHtml(AccountReadyPayload payload) {
        if (emailTemplate == null) {
            return null;
        }
        return emailTemplate
                .replace("{{fullName}}", escapeHtml(payload.fullName()))
                .replace("{{branch}}", escapeHtml(payload.branch()))
                .replace("{{accountNumber}}", escapeHtml(payload.accountNumber()))
                .replace("{{loginUrl}}", loginUrl)
                .replace("{{currentYear}}", String.valueOf(Year.now().getValue()));
    }

    private String loadTemplate(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.warn("Email template not found on classpath: {}. Falling back to plain text.", path);
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to load email template: {}. Falling back to plain text.", path, e);
            return null;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
