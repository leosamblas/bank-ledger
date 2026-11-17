package com.bankledger.notification.application.service;

import com.bankledger.commons.events.v1.account.AccountReadyPayload;
import com.bankledger.notification.config.EmailNotificationProperties;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationService Unit Tests")
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private Optional<JavaMailSender> optionalMailSender;
    private EmailTemplateLoader templateLoader;
    private EmailNotificationProperties properties;
    private Session mailSession;
    private AccountReadyPayload payload;

    @BeforeEach
    void setUp() {
        mailSession = Session.getInstance(new Properties());
        lenient().when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(mailSession));
        optionalMailSender = Optional.of(mailSender);

        templateLoader = new EmailTemplateLoader();
        properties = new EmailNotificationProperties();
        properties.setEnabled(true);
        properties.setFrom("noreply@bankledger.com");
        properties.setLoginUrl("http://localhost:3000/login");

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
        properties.setEnabled(false);
        EmailNotificationService service = new EmailNotificationService(java.util.Optional.of(mailSender), properties, templateLoader);

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .verifyComplete();

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should skip SMTP gracefully when mailSender is empty Optional")
    void whenMailSenderIsEmpty_shouldNotThrowAndReturnEmptyMono() {
        EmailNotificationService service = new EmailNotificationService(java.util.Optional.empty(), properties, templateLoader);

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should construct rich MimeMessage and send via mailSender when mailEnabled is true")
    void whenMailEnabled_shouldConstructCorrectMimeMessageAndSend() throws MessagingException, IOException {
        EmailNotificationService service = new EmailNotificationService(java.util.Optional.of(mailSender), properties, templateLoader);

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getFrom()).hasSize(1);
        assertThat(sentMessage.getFrom()[0].toString()).contains("noreply@bankledger.com");

        Address[] recipients = sentMessage.getAllRecipients();
        assertThat(recipients).hasSize(1);
        assertThat(recipients[0].toString()).contains("alice@example.com");

        assertThat(sentMessage.getSubject()).isEqualTo("Bem-vindo ao Nexus Bank! Sua conta está pronta para uso");

        String contentText = extractMessageContent(sentMessage);
        assertThat(contentText)
                .contains("Alice Silva")
                .contains("0001")
                .contains("123456-7")
                .contains("Nexus Bank")
                .contains("http://localhost:3000/login");
    }

    @Test
    @DisplayName("Should propagate error when mailSender throws MailException")
    void whenMailEnabledAndMailSenderFails_shouldPropagateError() {
        EmailNotificationService service = new EmailNotificationService(optionalMailSender, properties, templateLoader);

        doThrow(new MailSendException("SMTP connection timed out"))
                .when(mailSender).send(any(MimeMessage.class));

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .expectError(MailSendException.class)
                .verify();
    }

    @Test
    @DisplayName("Should fallback to default fromAddress when given blank fromAddress")
    void whenFromAddressIsBlank_shouldFallbackToDefaultFromAddress() throws MessagingException {
        properties.setFrom("   ");
        EmailNotificationService service = new EmailNotificationService(optionalMailSender, properties, templateLoader);

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getFrom()[0].toString()).contains("noreply@bankledger.com");
    }

    @Test
    @DisplayName("Should throw NullPointerException when payload is null")
    void whenPayloadIsNull_shouldThrowNullPointerException() {
        EmailNotificationService service = new EmailNotificationService(optionalMailSender, properties, templateLoader);

        assertThatThrownBy(() -> service.sendAccountReadyEmail(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("payload must not be null");
    }

    @Test
    @DisplayName("Should sanitize HTML special characters in customer full name")
    void shouldSanitizeHtmlSpecialCharactersInFullName() {
        EmailNotificationService service = new EmailNotificationService(optionalMailSender, properties, templateLoader);

        AccountReadyPayload xssPayload = new AccountReadyPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test@example.com",
                "<script>alert('XSS')</script> & Companhia",
                "0001",
                "123456-7",
                Instant.now()
        );

        String html = service.renderHtml(xssPayload);
        assertThat(html)
                .doesNotContain("<script>alert('XSS')</script>")
                .contains("&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt; &amp; Companhia");
    }

    @Test
    @DisplayName("Should fallback to plain-text dispatch when template loader returns null")
    void whenTemplateMissing_shouldFallbackToPlainText() throws MessagingException, IOException {
        EmailTemplateLoader mockLoader = mock(EmailTemplateLoader.class);
        when(mockLoader.loadTemplate(any())).thenReturn(null);

        EmailNotificationService service = new EmailNotificationService(optionalMailSender, properties, mockLoader);

        Mono<Void> result = service.sendAccountReadyEmail(payload);

        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getSubject()).isEqualTo("Bem-vindo ao Nexus Bank! Sua conta está pronta para uso");

        String contentText = extractMessageContent(sentMessage);
        assertThat(contentText)
                .contains("Alice Silva")
                .contains("0001")
                .contains("123456-7");
    }

    @Test
    @DisplayName("Should handle empty strings in payload fields cleanly without throwing")
    void whenPayloadHasEmptyStrings_shouldRenderCleanly() {
        EmailNotificationService service = new EmailNotificationService(optionalMailSender, properties, templateLoader);

        AccountReadyPayload emptyPayload = new AccountReadyPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test@example.com",
                "",
                "",
                "",
                Instant.now()
        );

        String html = service.renderHtml(emptyPayload);
        assertThat(html).isNotNull().isNotEmpty();

        String plainText = service.buildPlainText(emptyPayload);
        assertThat(plainText).isNotNull().contains("Olá ,");
    }

    private String extractMessageContent(Part part) throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                sb.append(extractMessageContent(multipart.getBodyPart(i))).append("\n");
            }
            return sb.toString();
        }
        if (content instanceof java.io.InputStream) {
            return new String(((java.io.InputStream) content).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return content != null ? content.toString() : "";
    }
}
