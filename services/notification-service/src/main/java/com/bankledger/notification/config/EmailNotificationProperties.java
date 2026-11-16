package com.bankledger.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mail")
public class EmailNotificationProperties {

    /**
     * Whether email sending via SMTP is enabled.
     */
    private boolean enabled = false;

    /**
     * From address for outbound emails.
     */
    private String from = "noreply@bankledger.com";

    /**
     * Base login URL for account onboarding links.
     */
    private String loginUrl = "http://localhost:3000/login";

    public String getFrom() {
        return (from != null && !from.isBlank()) ? from : "noreply@bankledger.com";
    }

    public String getLoginUrl() {
        return (loginUrl != null && !loginUrl.isBlank()) ? loginUrl : "http://localhost:3000/login";
    }
}
