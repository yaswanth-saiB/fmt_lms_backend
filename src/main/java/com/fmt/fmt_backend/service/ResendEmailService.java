package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.config.ResendProperties;
import com.fmt.fmt_backend.entity.Enquiry;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResendEmailService {

    private final Resend resend;
    private final ResendProperties properties;
    private final TemplateEngine templateEngine;

    private boolean emailEnabled;
    private String archiveEmail;
    private final Map<EmailType, SenderInfo> senderMap = new EnumMap<>(EmailType.class);

    public enum EmailType {
        OTP, WELCOME, RECORDING, PROMO, SUPPORT, ADMIN, ENQUIRY
    }

    @lombok.Value
    private static class SenderInfo {
        String email;
        String name;
        boolean bccArchive;
    }

    @PostConstruct
    public void init() {
        emailEnabled = properties.isEnabled();

        if (properties.getArchive().isEnabled()) {
            archiveEmail = properties.getArchive().getEmail();
        }

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
        // RECORDING shares the welcome sender
        senderMap.put(EmailType.RECORDING, new SenderInfo(
                properties.getSenders().get("welcome").getEmail(),
                properties.getSenders().get("welcome").getName(),
                properties.getSenders().get("welcome").isBccArchive()
        ));
        // ENQUIRY uses admin sender without BCC
        senderMap.put(EmailType.ENQUIRY, new SenderInfo(
                properties.getSenders().get("admin").getEmail(),
                properties.getSenders().get("admin").getName(),
                false
        ));

        log.info("✅ ResendEmailService initialized with {} sender types", senderMap.size());
    }

    @Async
    public void sendOtpEmail(String to, String otp, int expiryMinutes) {
        SenderInfo sender = senderMap.get(EmailType.OTP);
        sendEmail(to, "Your First Million Trade Verification Code",
                buildOtpTemplate(otp, expiryMinutes), sender, EmailType.OTP);
    }

    @Async
    public void sendWelcomeEmail(String to, String firstName, String role) {
        SenderInfo sender = senderMap.get(EmailType.WELCOME);
        sendEmail(to, "Welcome to First Million Trade, " + firstName + "!",
                buildWelcomeTemplate(firstName, role), sender, EmailType.WELCOME);
    }

    @Async
    public void sendPromotionalEmail(String to, String firstName, String campaign) {
        SenderInfo sender = senderMap.get(EmailType.PROMO);
        sendEmail(to, "First Million Trade - " + campaign,
                buildPromoTemplate(firstName, campaign), sender, EmailType.PROMO);
    }

    @Async
    public void sendSupportEmail(String to, String subject, String message) {
        SenderInfo sender = senderMap.get(EmailType.SUPPORT);
        sendEmail(to, subject, buildSupportTemplate(message), sender, EmailType.SUPPORT);
    }

    @Async
    public void sendAdminEmail(String to, String subject, String message) {
        SenderInfo sender = senderMap.get(EmailType.ADMIN);
        sendEmail(to, subject, buildAdminTemplate(message), sender, EmailType.ADMIN);
    }

    @Async
    public void sendRecordingAvailableEmail(String to, String firstName,
                                            String recordingTitle, String batchName,
                                            String recordingUrl) {
        SenderInfo sender = senderMap.get(EmailType.RECORDING);

        Context ctx = new Context();
        ctx.setVariable("firstName",      firstName);
        ctx.setVariable("recordingTitle", recordingTitle);
        ctx.setVariable("batchName",      batchName);
        ctx.setVariable("recordingUrl",   recordingUrl);
        ctx.setVariable("year",           LocalDateTime.now().getYear());
        String htmlContent = templateEngine.process("email/recording-available-email", ctx);

        sendEmail(to, "Recording Available — " + recordingTitle,
                htmlContent, sender, EmailType.RECORDING);
    }

    @Async
    public void sendEnquiryNotification(Enquiry enquiry) {
        SenderInfo sender = senderMap.get(EmailType.ENQUIRY);
        sendEmail(properties.getAdminEmail(), "New Enquiry Received - First Million Trade",
                buildEnquiryTemplate(enquiry), sender, EmailType.ENQUIRY);
    }

    private void sendEmail(String to, String subject, String htmlContent,
                           SenderInfo sender, EmailType type) {
        if (!emailEnabled) {
            log.info("📧 [{}] Would send to: {} (email disabled)", type, maskEmail(to));
            return;
        }

        try {
            String from = sender.getName() + " <" + sender.getEmail() + ">";

            CreateEmailOptions.Builder builder = CreateEmailOptions.builder()
                    .from(from)
                    .to(List.of(to))
                    .subject(subject)
                    .html(htmlContent);

            if (sender.isBccArchive() && archiveEmail != null) {
                builder.bcc(List.of(archiveEmail));
                log.debug("📋 BCC added to archive for {} email", type);
            }

            CreateEmailResponse response = resend.emails().send(builder.build());
            log.info("✅ [{}] Email sent to {} (ID: {})", type, maskEmail(to), response.getId());

        } catch (ResendException e) {
            log.error("❌ [{}] Failed to send to {}: {}", type, maskEmail(to), e.getMessage());
        }
    }

    private String buildEnquiryTemplate(Enquiry enquiry) {
        Context ctx = new Context();
        ctx.setVariable("name",            enquiry.getName());
        ctx.setVariable("mobile",          enquiry.getMobile());
        ctx.setVariable("city",            enquiry.getCity());
        ctx.setVariable("experienceLevel", enquiry.getExperienceLevel());
        ctx.setVariable("areaOfInterest",  enquiry.getAreaOfInterest());
        ctx.setVariable("message",         enquiry.getMessage());
        ctx.setVariable("ipAddress",       enquiry.getIpAddress() != null ? enquiry.getIpAddress() : "Unknown");
        ctx.setVariable("receivedAt",      enquiry.getCreatedAt() != null
                ? enquiry.getCreatedAt().toString() : LocalDateTime.now().toString());
        ctx.setVariable("year",            LocalDateTime.now().getYear());
        return templateEngine.process("email/enquiry-email", ctx);
    }

    private String buildOtpTemplate(String otp, int expiryMinutes) {
        Context ctx = new Context();
        ctx.setVariable("otp", otp);
        ctx.setVariable("expiryMinutes", expiryMinutes);
        ctx.setVariable("year", LocalDateTime.now().getYear());
        return templateEngine.process("email/otp-email", ctx);
    }

    private String buildWelcomeTemplate(String firstName, String role) {
        Context ctx = new Context();
        ctx.setVariable("firstName", firstName);
        ctx.setVariable("dashboardUrl", "https://firstmilliontrade.com/dashboard");
        ctx.setVariable("year", LocalDateTime.now().getYear());
        return templateEngine.process("email/welcome-email", ctx);
    }

    private String buildPromoTemplate(String firstName, String campaign) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0;">
                <div style="max-width: 560px; margin: 40px auto; background: #ffffff; border-radius: 8px; padding: 40px;">
                    <h2 style="color: #1a1a1a;">Hello %s!</h2>
                    <p style="color: #555;">Here's what's new at First Million Trade:</p>
                    <p style="color: #1a1a1a; font-weight: bold;">%s</p>
                    <div style="margin: 32px 0;">
                        <a href="https://firstmilliontrade.com/courses"
                           style="background-color: #1a1a1a; color: #ffffff; padding: 14px 28px;
                                  text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Explore Now
                        </a>
                    </div>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 32px 0;">
                    <p style="font-size: 12px; color: #999;">
                        &copy; First Million Trade. &nbsp;
                        <a href="https://firstmilliontrade.com/unsubscribe" style="color: #999;">Unsubscribe</a>
                    </p>
                </div>
            </body>
            </html>
            """, firstName, campaign);
    }

    private String buildSupportTemplate(String message) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0;">
                <div style="max-width: 560px; margin: 40px auto; background: #ffffff; border-radius: 8px; padding: 40px;">
                    <h2 style="color: #1a1a1a;">First Million Trade Support</h2>
                    <p style="color: #555; line-height: 1.6;">%s</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 32px 0;">
                    <p style="font-size: 12px; color: #999;">
                        First Million Trade Support &mdash; We're here to help.<br>
                        <a href="mailto:help@mail.firstmilliontrade.com" style="color: #999;">help@mail.firstmilliontrade.com</a>
                    </p>
                </div>
            </body>
            </html>
            """, message);
    }

    private String buildAdminTemplate(String message) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0;">
                <div style="max-width: 560px; margin: 40px auto; background: #ffffff; border-radius: 8px; padding: 40px;">
                    <h2 style="color: #1a1a1a;">Admin Notification</h2>
                    <p style="color: #555; line-height: 1.6;">%s</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 32px 0;">
                    <p style="font-size: 12px; color: #999;">
                        Automated notification &mdash; First Million Trade
                    </p>
                </div>
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
