package com.bankledger.notification.application.service;

import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import com.bankledger.notification.config.EmailNotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final Optional<JavaMailSender> mailSender;
    private final EmailNotificationProperties properties;
    private final EmailTemplateLoader templateLoader;

    public Mono<Void> sendAccountReadyEmail(AccountReadyPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");

        if (!properties.isEnabled() || mailSender.isEmpty()) {
            log.info("Email sending is DISABLED (feature flag app.mail.enabled=false). Skipping SMTP for customer: {} (email: {})",
                    payload.customerId(), payload.email());
            return Mono.empty();
        }

        return Mono.<Void>fromRunnable(() -> {
            try {
                JavaMailSender sender = mailSender.get();
                MimeMessage mimeMessage = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(
                        mimeMessage,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                        StandardCharsets.UTF_8.name()
                );

                String fromAddress = properties.getFrom();
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
                sender.send(mimeMessage);
                log.info("Successfully sent account ready email to: {} for customer: {}", payload.email(), payload.customerId());
            } catch (MessagingException e) {
                log.error("Failed to prepare MimeMessage for customer: {}", payload.customerId(), e);
                throw new MailPreparationException("Failed to prepare email message", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    String buildPlainText(AccountReadyPayload payload) {
        String name = payload.fullName() != null ? payload.fullName() : "";
        String branch = payload.branch() != null ? payload.branch() : "";
        String account = payload.accountNumber() != null ? payload.accountNumber() : "";
        return String.format(
                "Olá %s,%n%nSua conta no Nexus Bank está pronta para uso!%nAgência: %s%nConta: %s%n%nAcesse sua conta em: %s%n%nObrigado por escolher o Nexus Bank.",
                name, branch, account, properties.getLoginUrl()
        );
    }

    String renderHtml(AccountReadyPayload payload) {
        String template = templateLoader.loadTemplate("templates/account-ready-email.html");
        if (template == null) {
            return null;
        }
        return template
                .replace("{{fullName}}", escapeHtml(payload.fullName()))
                .replace("{{branch}}", escapeHtml(payload.branch()))
                .replace("{{accountNumber}}", escapeHtml(payload.accountNumber()))
                .replace("{{loginUrl}}", escapeHtml(properties.getLoginUrl()))
                .replace("{{currentYear}}", String.valueOf(Year.now().getValue()));
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
