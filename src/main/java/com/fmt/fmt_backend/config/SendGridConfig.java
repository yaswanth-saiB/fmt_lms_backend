package com.fmt.fmt_backend.config;

import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SendGridConfig {

    private final SendGridProperties properties;  // ✅ Inject the properties

    @Bean
    public SendGrid sendGrid() {
        return new SendGrid(properties.getApiKey());  // ✅ Get from properties
    }

    @Bean
    public Email archiveEmail() {
        if (properties.getArchive().isEnabled()) {
            return new Email(
                    properties.getArchive().getEmail(),
                    properties.getArchive().getName()
            );
        }
        return null; // Archive disabled
    }
}