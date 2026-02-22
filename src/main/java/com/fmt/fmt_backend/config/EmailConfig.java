package com.fmt.fmt_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
@ConfigurationProperties(prefix = "email")
@Data
public class EmailConfig {

    private Mailbox otp;
    private Mailbox info;
    private Mailbox help;
    private Mailbox archive;
    private Mailbox admin;

    // ✅ Base properties (your working config, centralized!)
    private Map<String, String> baseProperties = new HashMap<>() {{
        put("mail.transport.protocol", "smtp");
        put("mail.smtp.auth", "true");
        //put("mail.smtps.ssl.enable", "false");
        put("mail.smtp.starttls.enable", "true");
        put("mail.smtp.starttls.required", "true"); // added for railway deployment

        put("mail.smtp.connectiontimeout", "30000");
        put("mail.smtp.timeout", "10000");
        put("mail.smtp.writetimeout", "10000");
        put("mail.debug", "true");
    }};

    @Data
    public static class Mailbox {
        private String host;
        private int port;
        private String username;
        private String password;
        private String from;
        private Map<String, String> properties = new HashMap<>();
    }

    @Bean
    public JavaMailSender otpMailSender() {
        return createMailSender(otp);
    }

    @Bean
    public JavaMailSender infoMailSender() {
        return createMailSender(info);
    }

    @Bean
    public JavaMailSender helpMailSender() {
        return createMailSender(help);
    }

    @Bean
    public JavaMailSender archiveMailSender() {
        return createMailSender(archive);
    }

    @Bean
    public JavaMailSender adminMailSender() {
        return createMailSender(admin);
    }

    /**
     * ✅ REUSABLE method - replaces your hardcoded version
     */
    private JavaMailSender createMailSender(Mailbox mailbox) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailbox.getHost());
        sender.setPort(mailbox.getPort());
        sender.setUsername(mailbox.getUsername());
        sender.setPassword(mailbox.getPassword());
        //sender.setProtocol("smtps");
        sender.setProtocol("smtp");

        Properties props = new Properties();

        // 1. Apply base properties (your working config)
        props.putAll(baseProperties);

        // 2. Add mailbox-specific trust setting
        //props.put("mail.smtps.ssl.trust", mailbox.getHost());
        props.put("mail.smtp.ssl.trust", mailbox.getHost());

        // 3. Apply any mailbox-specific overrides
        if (mailbox.getProperties() != null && !mailbox.getProperties().isEmpty()) {
            props.putAll(mailbox.getProperties());
        }

        sender.setJavaMailProperties(props);
        return sender;
    }
}