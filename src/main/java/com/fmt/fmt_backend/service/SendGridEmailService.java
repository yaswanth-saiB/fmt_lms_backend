package com.fmt.fmt_backend.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridEmailService {

    private final SendGrid sendGrid;
    private final Email fromEmail;

    @Value("${sendgrid.enabled:true}")
    private boolean emailEnabled;

    @Async
    public void sendOtpEmail(String to, String otp, int expiryMinutes) {
        if (!emailEnabled) {
            log.info("Email sending disabled - would send OTP to: {}", to);
            return;
        }

        try {
            Mail mail = createOtpEmail(to, otp, expiryMinutes);
            sendEmail(mail, "OTP");
        } catch (Exception e) {
            log.error("❌ Failed to send OTP email: {}", e.getMessage());
        }
    }

    @Async
    public void sendWelcomeEmail(String to, String firstName, String role) {
        if (!emailEnabled) {
            log.info("Email sending disabled - would send welcome to: {}", to);
            return;
        }

        try {
            Mail mail = createWelcomeEmail(to, firstName, role);
            sendEmail(mail, "WELCOME");
        } catch (Exception e) {
            log.error("❌ Failed to send welcome email: {}", e.getMessage());
        }
    }

    private Mail createOtpEmail(String to, String otp, int expiryMinutes) {
        Email toEmail = new Email(to);
        String subject = "Your Trading App Verification Code";

        String htmlContent = String.format("""
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

        Content content = new Content("text/html", htmlContent);
        return new Mail(fromEmail, subject, toEmail, content);
    }

    private Mail createWelcomeEmail(String to, String firstName, String role) {
        Email toEmail = new Email(to);
        String subject = "Welcome to Trading App, " + firstName + "!";

        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Welcome to Trading App, %s!</h2>
                <p>We're excited to have you on board.</p>
                <p>Get started by exploring our courses and completing your profile.</p>
            </body>
            </html>
            """, firstName);

        Content content = new Content("text/html", htmlContent);
        return new Mail(fromEmail, subject, toEmail, content);
    }

    private void sendEmail(Mail mail, String emailType) {
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✅ {} email sent successfully. Status: {}", emailType, response.getStatusCode());
            } else {
                log.error("❌ {} email failed. Status: {}, Body: {}",
                        emailType, response.getStatusCode(), response.getBody());
            }
        } catch (IOException e) {
            log.error("❌ Exception sending {} email: {}", emailType, e.getMessage());
        }
    }
}
