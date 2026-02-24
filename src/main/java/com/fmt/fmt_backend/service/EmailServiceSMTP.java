package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.config.EmailConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceSMTP {

    // All 5 mail senders injected
    private final JavaMailSender otpMailSender;
    private final JavaMailSender infoMailSender;
    private final JavaMailSender helpMailSender;
    private final JavaMailSender archiveMailSender;
    private final JavaMailSender adminMailSender;

    private final EmailConfig emailConfig;
    private final TemplateEngine templateEngine;

    @Value("${app.enable-bcc:true}")
    private boolean enableBcc;

    @Value("${app.bcc-address:archive@xyzt.com}")
    private String bccAddress;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // ========== PUBLIC METHODS ==========

    /**
     * Send OTP email using OTP mailbox
     * Auto-BCC to archive
     */
    @Async
    public void sendOtpEmail(String to, String otp, int expiryMinutes) {
        log.info("📧 Sending OTP email to: {}", maskEmail(to));

        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("otp", otp);
        templateModel.put("expiryMinutes", expiryMinutes);
        templateModel.put("year", LocalDateTime.now().getYear());

        String subject = "Your verification code for Trading App";
        String htmlContent = buildTemplate("otp-email", templateModel);
        String plainContent = buildPlainOtp(otp, expiryMinutes);

        sendEmailWithBcc(
                to,
                subject,
                htmlContent,
                plainContent,
                otpMailSender,
                emailConfig.getOtp().getFrom(),
                "OTP"
        );
    }

    /**
     * Send welcome email using INFO mailbox
     * Auto-BCC to archive
     */
    @Async
    public void sendWelcomeEmail(String to, String firstName, String role) {
        log.info("📧 Sending welcome email to: {}", maskEmail(to));

        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("firstName", firstName);
        templateModel.put("role", role);
        templateModel.put("frontendUrl", frontendUrl);
        templateModel.put("year", LocalDateTime.now().getYear());

        String subject = "Welcome to Trading App, " + firstName + "!";
        String htmlContent = buildTemplate("welcome-email", templateModel);
        String plainContent = buildPlainWelcome(firstName, role);

        sendEmailWithBcc(
                to,
                subject,
                htmlContent,
                plainContent,
                infoMailSender,
                emailConfig.getInfo().getFrom(),
                "WELCOME"
        );
    }

    /**
     * Send promotional email using INFO mailbox
     * Auto-BCC to archive
     */
    @Async
    public void sendPromotionalEmail(String to, String firstName, String campaignName) {
        log.info("📧 Sending promotional email to: {}", maskEmail(to));

        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("firstName", firstName);
        templateModel.put("campaign", campaignName);
        templateModel.put("frontendUrl", frontendUrl);
        templateModel.put("unsubscribeUrl", frontendUrl + "/unsubscribe?email=" + to);
        templateModel.put("year", LocalDateTime.now().getYear());

        String subject = "Trading App - " + campaignName;
        String htmlContent = buildTemplate("promo-email", templateModel);
        String plainContent = buildPlainPromo(firstName, campaignName);

        sendEmailWithBcc(
                to,
                subject,
                htmlContent,
                plainContent,
                infoMailSender,
                emailConfig.getInfo().getFrom(),
                "PROMO"
        );
    }

    /**
     * Send admin notification using ADMIN mailbox
     * Auto-BCC to archive
     */
    @Async
    public void sendAdminEmail(String to, String subject, String message) {
        log.info("📧 Sending admin email to: {}", maskEmail(to));

        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("message", message);
        templateModel.put("year", LocalDateTime.now().getYear());

        String htmlContent = buildTemplate("admin-email", templateModel);
        String plainContent = message;

        sendEmailWithBcc(
                to,
                "[Admin] " + subject,
                htmlContent,
                plainContent,
                adminMailSender,
                emailConfig.getAdmin().getFrom(),
                "ADMIN"
        );
    }

    /**
     * Send help/support reply using HELP mailbox
     * No BCC to keep support conversations private
     */
    @Async
    public void sendSupportEmail(String to, String subject, String message) {
        log.info("📧 Sending support email to: {}", maskEmail(to));

        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("message", message);
        templateModel.put("year", LocalDateTime.now().getYear());

        String htmlContent = buildTemplate("support-email", templateModel);

        try {
            MimeMessage mimeMessage = helpMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(emailConfig.getHelp().getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            helpMailSender.send(mimeMessage);

            log.info("✅ Support email sent to: {}", maskEmail(to));

        } catch (Exception e) {
            log.error("❌ Failed to send support email: {}", e.getMessage());
        }
    }

    // ========== PRIVATE METHODS ==========

    /**
     * Core email sending method with BCC archiving
     */
    private void sendEmailWithBcc(String to, String subject, String htmlContent,
                                  String plainContent, JavaMailSender sender,
                                  String from, String emailType) {

        long startTime = System.currentTimeMillis();

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set email details
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainContent, htmlContent);

            // ⭐ AUTO-BCC TO ARCHIVE (unless disabled)
            if (enableBcc) {
                helper.setBcc(bccAddress);
            }

            // Send email
            sender.send(message);

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ {} email sent to {} in {}ms", emailType, maskEmail(to), duration);

        } catch (Exception e) {
            log.error("❌ Failed to send {} email to {}: {}",
                    emailType, maskEmail(to), e.getMessage());
        }
    }

    /**
     * Build HTML email using Thymeleaf templates
     */
    private String buildTemplate(String templateName, Map<String, Object> model) {
        try {
            Context context = new Context();
            context.setVariables(model);
            return templateEngine.process("email/" + templateName, context);
        } catch (Exception e) {
            log.warn("Template processing failed, using fallback: {}", e.getMessage());
            return buildFallbackHtml(model);
        }
    }

    /**
     * Fallback HTML when templates aren't available
     */
    private String buildFallbackHtml(Map<String, Object> model) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><body>");
        html.append("<h2>Trading App</h2>");
        model.forEach((key, value) -> {
            html.append("<p><strong>").append(key).append(":</strong> ").append(value).append("</p>");
        });
        html.append("</body></html>");
        return html.toString();
    }

    // Plain text templates (fallback)
    private String buildPlainOtp(String otp, int expiryMinutes) {
        return String.format("""
            Your verification code is: %s
            
            This code will expire in %d minutes.
            
            If you didn't request this, please ignore this email.
            
            -- Trading App
            """, otp, expiryMinutes);
    }

    private String buildPlainWelcome(String firstName, String role) {
        return String.format("""
            Welcome to Trading App, %s!
            
            We're excited to have you onboard.
            
            Get started by exploring our courses and completing your profile.
            
            -- Trading App
            """, firstName);
    }

    private String buildPlainPromo(String firstName, String campaign) {
        return String.format("""
            Hello %s,
            
            Check out what's new at Trading App: %s
            
            To unsubscribe from future emails, visit: %s/unsubscribe
            
            -- Trading App
            """, firstName, campaign, frontendUrl);
    }

    /**
     * Mask email for logging (privacy)
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "invalid";
        String[] parts = email.split("@");
        if (parts[0].length() <= 3) return "***@" + parts[1];
        return parts[0].substring(0, 3) + "***@" + parts[1];
    }
}
