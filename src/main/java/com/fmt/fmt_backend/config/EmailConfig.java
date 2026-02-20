package com.fmt.fmt_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
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

    @Data
    public static class Mailbox {
        private String host;
        private int port;
        private String username;
        private String password;
        private String from;
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

    private JavaMailSender createMailSender(Mailbox mailbox) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailbox.getHost());
        sender.setPort(mailbox.getPort());
        sender.setUsername(mailbox.getUsername());
        sender.setPassword(mailbox.getPassword());

        // CRITICAL FIX: Enable SSL
        sender.setProtocol("smtps");  // Use smtps for SSL

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtps");
        props.put("mail.smtps.auth", "true");
        props.put("mail.smtps.ssl.enable", "true");  // Enable SSL
        props.put("mail.smtps.starttls.enable", "false");  // Not needed for SSL
        props.put("mail.smtps.ssl.trust", mailbox.getHost());  // Trust the host
        props.put("mail.smtps.connectiontimeout", "10000");  // 10 seconds
        props.put("mail.smtps.timeout", "10000");  // 10 seconds
        props.put("mail.smtps.writetimeout", "10000");  // 10 seconds

        // Debug to see what's happening
        props.put("mail.debug", "true");

        return sender;
    }
}