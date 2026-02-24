package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.config.SendGridProperties;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridEmailService {

    private final SendGrid sendGrid;
    private final SendGridProperties properties;

    private Email archiveEmail;
    private final Map<EmailType, SenderInfo> senderMap = new EnumMap<>(EmailType.class);

    public enum EmailType {
        OTP, WELCOME, PROMO, SUPPORT, ADMIN
    }

    @lombok.Value
    private static class SenderInfo {
        String email;
        String name;
        boolean bccArchive;
    }

    @PostConstruct
    public void init() {
        // Create archive email if enabled
        if (properties.getArchive().isEnabled()) {
            archiveEmail = new Email(
                    properties.getArchive().getEmail(),
                    properties.getArchive().getName()
            );
        }

        // Map each email type to its configuration
        senderMap.put(EmailType.OTP, new SenderInfo(
                properties.getSenders().get("otp").getEmail(),
                properties.getSenders().get("otp").getName(),
                properties.getSenders().get("otp").isBccArchive()
        ));

        senderMap.put(EmailType.WELCOME, new SenderInfo(
                properties.getSenders().get("welcome").getEmail(),
                properties.getSenders().get("welcome").getName(),
                properties.getSenders().get("welcome").isBccArchive()
        ));

        senderMap.put(EmailType.PROMO, new SenderInfo(
                properties.getSenders().get("promo").getEmail(),
                properties.getSenders().get("promo").getName(),
                properties.getSenders().get("promo").isBccArchive()
        ));

        senderMap.put(EmailType.SUPPORT, new SenderInfo(
                properties.getSenders().get("support").getEmail(),
                properties.getSenders().get("support").getName(),
                properties.getSenders().get("support").isBccArchive()
        ));

        senderMap.put(EmailType.ADMIN, new SenderInfo(
                properties.getSenders().get("admin").getEmail(),
                properties.getSenders().get("admin").getName(),
                properties.getSenders().get("admin").isBccArchive()
        ));

        log.info("✅ SendGridEmailService initialized with {} sender types", senderMap.size());
    }

    @Async
    public void sendOtpEmail(String to, String otp, int expiryMinutes) {
        SenderInfo sender = senderMap.get(EmailType.OTP);
        String subject = "Your FMT App Verification Code";
        String htmlContent = buildOtpTemplate(otp, expiryMinutes);

        sendEmail(to, subject, htmlContent, sender, EmailType.OTP);
    }

    @Async
    public void sendWelcomeEmail(String to, String firstName, String role) {
        SenderInfo sender = senderMap.get(EmailType.WELCOME);
        String subject = "Welcome to FMT App, " + firstName + "!";
        String htmlContent = buildWelcomeTemplate(firstName, role);

        sendEmail(to, subject, htmlContent, sender, EmailType.WELCOME);
    }

    @Async
    public void sendPromotionalEmail(String to, String firstName, String campaign) {
        SenderInfo sender = senderMap.get(EmailType.PROMO);
        String subject = "FMT App - " + campaign;
        String htmlContent = buildPromoTemplate(firstName, campaign);

        sendEmail(to, subject, htmlContent, sender, EmailType.PROMO);
    }

    @Async
    public void sendSupportEmail(String to, String subject, String message) {
        SenderInfo sender = senderMap.get(EmailType.SUPPORT);
        String htmlContent = buildSupportTemplate(message);

        sendEmail(to, subject, htmlContent, sender, EmailType.SUPPORT);
    }

    @Async
    public void sendAdminEmail(String to, String subject, String message) {
        SenderInfo sender = senderMap.get(EmailType.ADMIN);
        String htmlContent = buildAdminTemplate(message);

        sendEmail(to, subject, htmlContent, sender, EmailType.ADMIN);
    }

    private void sendEmail(String to, String subject, String htmlContent,
                           SenderInfo sender, EmailType type) {
        if (!properties.isEnabled()) {
            log.info("📧 [{}] Would send to: {} (email disabled)", type, maskEmail(to));
            return;
        }

        try {
            Email from = new Email(sender.getEmail(), sender.getName());
            Email toEmail = new Email(to);
            Content content = new Content("text/html", htmlContent);

            // ✅ Create mail with proper TO recipient
            Mail mail = new Mail(from, subject, toEmail, content);

            // ✅ Add BCC to archive separately (not as additional personalization)
            if (sender.isBccArchive() && archiveEmail != null) {
                mail.personalization.get(0).addBcc(archiveEmail);
                log.debug("📋 BCC added to archive for {} email", type);
            }

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✅ [{}] Email sent to {} (Status: {})",
                        type, maskEmail(to), response.getStatusCode());
            } else {
                log.error("❌ [{}] Failed to send to {}: Status {}, Body {}",
                        type, maskEmail(to), response.getStatusCode(), response.getBody());
            }

        } catch (IOException e) {
            log.error("❌ [{}] Exception sending to {}: {}",
                    type, maskEmail(to), e.getMessage());
        }
    }

    // Template methods (same as before)
    private String buildOtpTemplate(String otp, int expiryMinutes) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Verification Code</h2>
                <p>Your verification code is:</p>
                <div style="background-color: #f0f0f0; padding: 20px; text-align: center;">
                    <span style="font-size: 36px; font-weight: bold;">%s</span>
                </div>
                <p>This code will expire in %d minutes.</p>
            </body>
            </html>
            """, otp, expiryMinutes);
    }

    private String buildWelcomeTemplate(String firstName, String role) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Welcome to FMT App, %s!</h2>
                <p>We're excited to have you on board as a %s.</p>
                <p>Get started by exploring our courses and completing your profile.</p>
            </body>
            </html>
            """, firstName, role);
    }

    private String buildPromoTemplate(String firstName, String campaign) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Hello %s!</h2>
                <p>Check out what's new at FMT App:</p>
                <p><strong>%s</strong></p>
                <p><a href="https://yourapp.com/courses">Explore Now</a></p>
                <p style="font-size: 12px; color: #999;">
                    To unsubscribe, <a href="https://yourapp.com/unsubscribe">click here</a>
                </p>
            </body>
            </html>
            """, firstName, campaign);
    }

    private String buildSupportTemplate(String message) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <p>%s</p>
                <hr>
                <p style="font-size: 12px; color: #999;">
                    FMT App Support - We're here to help!
                </p>
            </body>
            </html>
            """, message);
    }

    private String buildAdminTemplate(String message) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <p>%s</p>
                <hr>
                <p style="font-size: 12px; color: #999;">
                    This is an automated admin notification.
                </p>
            </body>
            </html>
            """, message);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "invalid";
        String[] parts = email.split("@");
        if (parts[0].length() <= 3) return "***@" + parts[1];
        return parts[0].substring(0, 3) + "***@" + parts[1];
    }
}