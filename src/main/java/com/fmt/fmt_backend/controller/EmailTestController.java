package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.config.EmailConfig;
import com.fmt.fmt_backend.dto.ApiResponse;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test/email")
@RequiredArgsConstructor
@Slf4j
public class EmailTestController {

    private final EmailConfig emailConfig;
    private final JavaMailSender otpMailSender;
    private final JavaMailSender infoMailSender;
    private final JavaMailSender helpMailSender;
    private final JavaMailSender archiveMailSender;
    private final JavaMailSender adminMailSender;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendOtpEmail(
            @RequestParam String to,
            @RequestParam String otp,
            @RequestParam(required = false, defaultValue = "5") int expiryMinutes) {

        log.info("📧 OTP email request to: {}", to);

        try {
            MimeMessage message = otpMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Create both HTML and plain text versions
            String htmlContent = createOtpHtmlTemplate(otp, expiryMinutes);
            String plainContent = createOtpPlainTemplate(otp, expiryMinutes);

            helper.setFrom(emailConfig.getOtp().getFrom());
            helper.setTo(to);
            helper.setSubject("Your verification code for Trading App");
            helper.setText(plainContent, htmlContent); // Both versions

            otpMailSender.send(message);

            Map<String, Object> response = new HashMap<>();
            response.put("to", to);
            response.put("mailbox", "otp");
            response.put("message", "OTP email sent");

            return ResponseEntity.ok(ApiResponse.success("Email sent", response));

        } catch (Exception e) {
            log.error("❌ Failed: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed: " + e.getMessage()));
        }
    }

    @PostMapping("/send-welcome")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendWelcomeEmail(
            @RequestParam String to,
            @RequestParam String firstName,
            @RequestParam(required = false, defaultValue = "STUDENT") String role) {

        log.info("📧 Welcome email to: {}", to);

        try {
            MimeMessage message = infoMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String htmlContent = createWelcomeHtmlTemplate(firstName, role);
            String plainContent = createWelcomePlainTemplate(firstName, role);

            helper.setFrom(emailConfig.getInfo().getFrom());
            helper.setTo(to);
            helper.setSubject("Welcome to Trading App, " + firstName + "!");
            helper.setText(plainContent, htmlContent);

            infoMailSender.send(message);

            Map<String, Object> response = new HashMap<>();
            response.put("to", to);
            response.put("firstName", firstName);
            response.put("mailbox", "info");

            return ResponseEntity.ok(ApiResponse.success("Welcome email sent", response));

        } catch (Exception e) {
            log.error("❌ Failed: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed: " + e.getMessage()));
        }
    }

    @PostMapping("/send-admin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendAdminEmail(
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String message) {

        log.info("📧 Admin email to: {}", to);

        try {
            MimeMessage mimeMessage = adminMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String htmlContent = createAdminHtmlTemplate(message);
            String plainContent = message;

            helper.setFrom(emailConfig.getAdmin().getFrom());
            helper.setTo(to);
            helper.setSubject("[Admin] " + subject);
            helper.setText(plainContent, htmlContent);

            adminMailSender.send(mimeMessage);

            Map<String, Object> response = new HashMap<>();
            response.put("to", to);
            response.put("subject", subject);

            return ResponseEntity.ok(ApiResponse.success("Admin email sent", response));

        } catch (Exception e) {
            log.error("❌ Failed: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed: " + e.getMessage()));
        }
    }

    // ========== HTML TEMPLATES (Spam-optimized) ==========

    private String createOtpHtmlTemplate(String otp, int expiryMinutes) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333333; margin: 0; padding: 0;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background-color: #f8f9fa; border-radius: 8px; padding: 30px; border: 1px solid #e9ecef;">
                        <h2 style="color: #2c3e50; margin-top: 0;">Verification Code</h2>
                        <p style="font-size: 16px;">Hello,</p>
                        <p style="font-size: 16px;">Your verification code is:</p>
                        <div style="background-color: #ffffff; border-radius: 6px; padding: 20px; margin: 20px 0; text-align: center; border: 1px solid #dee2e6;">
                            <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #0066cc;">%s</span>
                        </div>
                        <p style="font-size: 14px; color: #666666;">This code will expire in %d minutes.</p>
                        <p style="font-size: 14px; color: #666666;">If you didn't request this code, please ignore this email.</p>
                        <hr style="border: none; border-top: 1px solid #e9ecef; margin: 20px 0;">
                        <p style="font-size: 12px; color: #999999; text-align: center;">
                            Trading App - Learn and Grow<br>
                            This is an automated message, please do not reply.
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """, otp, expiryMinutes);
    }

    private String createWelcomeHtmlTemplate(String firstName, String role) {
        String roleSpecific = "STUDENT".equals(role) ?
                "Browse our courses and start learning at your own pace." :
                "Set up your mentor profile and create your first course.";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333333; margin: 0; padding: 0;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background-color: #ffffff; border-radius: 8px; padding: 30px; border: 1px solid #e9ecef;">
                        <h2 style="color: #2c3e50; margin-top: 0;">Welcome to Trading App, %s!</h2>
                        <p style="font-size: 16px;">We're excited to have you on board.</p>
                        <p style="font-size: 16px;">%s</p>
                        <div style="background-color: #f8f9fa; border-radius: 6px; padding: 20px; margin: 20px 0;">
                            <h3 style="color: #2c3e50; margin-top: 0;">Getting Started:</h3>
                            <ul style="padding-left: 20px;">
                                <li>Complete your profile</li>
                                <li>Explore available courses</li>
                                <li>Set your learning goals</li>
                            </ul>
                        </div>
                        <hr style="border: none; border-top: 1px solid #e9ecef; margin: 20px 0;">
                        <p style="font-size: 12px; color: #999999; text-align: center;">
                            Trading App - %s<br>
                            <a href="%s/unsubscribe" style="color: #999999;">Unsubscribe</a> from promotional emails
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """, firstName, roleSpecific,
                "STUDENT".equals(role) ? "Start your learning journey" : "Start sharing your knowledge",
                "http://localhost:3000");
    }

    private String createAdminHtmlTemplate(String message) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background-color: #ffffff; border-radius: 8px; padding: 30px;">
                        <p style="font-size: 16px;">%s</p>
                        <hr style="border: none; border-top: 1px solid #e9ecef; margin: 20px 0;">
                        <p style="font-size: 12px; color: #999999;">This is an administrative message.</p>
                    </div>
                </div>
            </body>
            </html>
            """, message);
    }

    // ========== PLAIN TEXT VERSIONS ==========

    private String createOtpPlainTemplate(String otp, int expiryMinutes) {
        return String.format("""
            Verification Code
            
            Hello,
            
            Your verification code is: %s
            
            This code will expire in %d minutes.
            
            If you didn't request this code, please ignore this email.
            
            --
            Trading App
            """, otp, expiryMinutes);
    }

    private String createWelcomePlainTemplate(String firstName, String role) {
        return String.format("""
            Welcome to Trading App, %s!
            
            We're excited to have you on board.
            
            Getting Started:
            - Complete your profile
            - Explore available courses
            - Set your learning goals
            
            --
            Trading App
            """, firstName);
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, String> admin = new HashMap<>();
        admin.put("from", emailConfig.getAdmin().getFrom());
        admin.put("username", emailConfig.getAdmin().getUsername());
        config.put("admin", admin);

        Map<String, String> otp = new HashMap<>();
        otp.put("from", emailConfig.getOtp().getFrom());
        otp.put("username", emailConfig.getOtp().getUsername());
        config.put("otp", otp);

        config.put("spam_prevention", "SPF/DKIM configured, HTML+Plain, No spam words");

        return ResponseEntity.ok(ApiResponse.success("Email config", config));
    }
}