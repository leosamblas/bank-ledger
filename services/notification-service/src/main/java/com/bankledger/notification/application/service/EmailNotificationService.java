package com.bankledger.notification.application.service;

import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;

@Slf4j
@Service
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String fromAddress;

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${spring.mail.username:noreply@bankledger.com}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.fromAddress = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : "noreply@bankledger.com";
    }

    public Mono<Void> sendAccountReadyEmail(AccountReadyPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");

        if (!mailEnabled) {
            log.info("Email sending is DISABLED (feature flag app.mail.enabled=false). Skipping SMTP for customer: {} (email: {})",
                    payload.customerId(), payload.email());
            return Mono.empty();
        }

        return Mono.<Void>fromRunnable(() -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(payload.email());
            message.setSubject("Bem-vindo ao BankLedger! Sua conta está pronta para uso");
            message.setText(String.format(
                    "Olá %s,%n%nSua conta no BankLedger está pronta para uso!%nAgência: %s%nConta: %s%n%nObrigado por escolher o BankLedger.",
                    payload.fullName(), payload.branch(), payload.accountNumber()
            ));

            log.debug("Dispatching account ready email to {} from {}", payload.email(), fromAddress);
            mailSender.send(message);
            log.info("Successfully sent account ready email to: {} for customer: {}", payload.email(), payload.customerId());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
