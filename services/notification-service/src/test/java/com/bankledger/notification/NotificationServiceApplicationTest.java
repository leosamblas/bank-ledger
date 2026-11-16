package com.bankledger.notification;

import com.bankledger.notification.application.service.EmailNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.mail.host=localhost",
        "app.mail.enabled=false",
        "management.health.mail.enabled=false"
})
class NotificationServiceApplicationTest {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired(required = false)
    private EmailNotificationService emailNotificationService;

    @Test
    @DisplayName("Spring context loads and EmailNotificationService bean is instantiated cleanly")
    void contextLoads() {
        assertThat(emailNotificationService).isNotNull();
    }
}
