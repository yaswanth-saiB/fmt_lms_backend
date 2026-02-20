package com.fmt.fmt_backend.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "twilio")
@Data
@Slf4j
public class TwilioConfig {

    private String accountSid;
    private String authToken;
    private String phoneNumber;

    @PostConstruct
    public void initTwilio() {
        try {
            Twilio.init(accountSid, authToken);
            log.info("✅ Twilio initialized successfully with phone: {}", phoneNumber);
        } catch (Exception e) {
            log.error("❌ Failed to initialize Twilio: {}", e.getMessage());
            // Don't throw - app can still run, SMS just won't work
        }
    }
}