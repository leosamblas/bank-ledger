package com.bankledger.notification;

import com.bankledger.notification.application.service.EmailNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceApplicationTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.kafka.bootstrap-servers=localhost:9092",
            "app.mail.enabled=false"
    })
    @DisplayName("When app.mail.enabled is false")
    class WhenMailDisabled {

        @Autowired(required = false)
        private EmailNotificationService emailNotificationService;

        @Autowired(required = false)
        private JavaMailSender mailSender;

        @Test
        @DisplayName("EmailNotificationService is instantiated, but JavaMailSender bean is NOT instantiated")
        void shouldNotInstantiateJavaMailSenderWhenDisabled() {
            assertThat(emailNotificationService).isNotNull();
            assertThat(mailSender).isNull();
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.mail.host=localhost",
            "app.mail.enabled=true"
    })
    @DisplayName("When app.mail.enabled is true")
    class WhenMailEnabled {

        @Autowired(required = false)
        private EmailNotificationService emailNotificationService;

        @Autowired(required = false)
        private JavaMailSender mailSender;

        @Test
        @DisplayName("JavaMailSender bean IS instantiated when app.mail.enabled is true")
        void shouldInstantiateJavaMailSenderWhenEnabled() {
            assertThat(emailNotificationService).isNotNull();
            assertThat(mailSender).isNotNull();
        }
    }
}
