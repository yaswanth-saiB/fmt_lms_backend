package com.fmt.fmt_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "sendgrid")
@Data
public class SendGridProperties {

    private String apiKey;
    private boolean enabled = true;
    private String adminEmail;  // Add this field
    private ArchiveConfig archive = new ArchiveConfig();
    private Map<String, SenderConfig> senders = new HashMap<>();

    @Data
    public static class ArchiveConfig {
        private boolean enabled = true;
        private String email = "archive@firstmilliontrade.com";
        private String name = "FNT Archive";
    }

    @Data
    public static class SenderConfig {
        private String email;
        private String name;
        private boolean bccArchive = false;
    }
}