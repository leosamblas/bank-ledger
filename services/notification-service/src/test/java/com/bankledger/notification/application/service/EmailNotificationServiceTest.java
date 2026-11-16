package com.bankledger.notification.application.service;

import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationService Unit Tests")
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private AccountReadyPayload payload;

    @BeforeEach
    void setUp() {
        payload = new AccountReadyPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "alice@example.com",
                "Alice Silva",
                "0001",
                "123456-7",
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should skip SMTP and return empty Mono when mailEnabled is false")
    void whenMailDisabled_shouldNotSendEmailAndReturnEmptyMono() {
        EmailNotificationService service = new EmailNotificationService(
                mailSender,
                false,
                "noreply@bankledger.com"
        );

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .verifyComplete();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should construct message and send via mailSender when mailEnabled is true")
    void whenMailEnabled_shouldConstructCorrectMessageAndSend() {
        EmailNotificationService service = new EmailNotificationService(
                mailSender,
                true,
                "noreply@bankledger.com"
        );

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getFrom()).isEqualTo("noreply@bankledger.com");
        assertThat(sentMessage.getTo()).containsExactly("alice@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("Bem-vindo ao BankLedger! Sua conta está pronta para uso");
        assertThat(sentMessage.getText())
                .contains("Alice Silva")
                .contains("0001")
                .contains("123456-7");
    }

    @Test
    @DisplayName("Should propagate error when mailSender throws MailException")
    void whenMailEnabledAndMailSenderFails_shouldPropagateError() {
        EmailNotificationService service = new EmailNotificationService(
                mailSender,
                true,
                "noreply@bankledger.com"
        );

        doThrow(new MailSendException("SMTP connection timed out"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .expectError(MailSendException.class)
                .verify();
    }

    @Test
    @DisplayName("Should fallback to default fromAddress when given blank fromAddress")
    void whenFromAddressIsBlank_shouldFallbackToDefaultFromAddress() {
        EmailNotificationService service = new EmailNotificationService(
                mailSender,
                true,
                "   "
        );

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getFrom()).isEqualTo("noreply@bankledger.com");
    }

    @Test
    @DisplayName("Should throw NullPointerException when payload is null")
    void whenPayloadIsNull_shouldThrowNullPointerException() {
        EmailNotificationService service = new EmailNotificationService(
                mailSender,
                true,
                "noreply@bankledger.com"
        );

        assertThatThrownBy(() -> service.sendAccountReadyEmail(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("payload must not be null");
    }
}
