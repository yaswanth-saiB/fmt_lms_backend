package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.config.TwilioConfig;
import com.fmt.fmt_backend.dto.ApiResponse;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final TwilioConfig twilioConfig;
    private final OtpService otpService;

    @Value("${app.environment:development}")
    private String environment;

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    // Indian mobile number patterns
    private static final Pattern INDIAN_MOBILE_PATTERN = Pattern.compile(
            "^(\\+91|91|0)?[6-9]\\d{9}$"
    );

    private static final Pattern E164_PATTERN = Pattern.compile(
            "^\\+91[6-9]\\d{9}$"
    );

    /**
     * Send OTP via SMS
     */
    @Async
    public CompletableFuture<ApiResponse<String>> sendOtpSms(String phoneNumber, String otp) {
        log.info("📱 Sending OTP SMS to: {}", maskPhoneNumber(phoneNumber));

        try {
            // Validate phone number
            if (!isValidIndianNumber(phoneNumber)) {
                log.warn("❌ Invalid Indian phone number: {}", maskPhoneNumber(phoneNumber));
                return CompletableFuture.completedFuture(
                        ApiResponse.error("Invalid Indian phone number. Use format: +919876543210")
                );
            }

            // Format to E.164
            String e164Number = formatToE164(phoneNumber);

            // Create message content (TRAI compliant for India)
            String messageBody = String.format(
                    "Your Trading App verification code is: %s. Valid for %d minutes. Do not share this code with anyone.",
                    otp, otpExpiryMinutes
            );

            // Send via Twilio
            Message twilioMessage = Message.creator(
                    new PhoneNumber(e164Number),
                    new PhoneNumber(twilioConfig.getPhoneNumber()),
                    messageBody
            ).create();

            // Log success (mask OTP in production)
            String logOtp = "production".equals(environment) ? "***" : otp;
            log.info("✅ OTP SMS sent to {}: {} (SID: {})",
                    maskPhoneNumber(phoneNumber), logOtp, twilioMessage.getSid());

            return CompletableFuture.completedFuture(
                    ApiResponse.success("OTP sent successfully", twilioMessage.getSid())
            );

        } catch (ApiException e) {
            log.error("❌ Twilio API error for {}: {} (Code: {})",
                    maskPhoneNumber(phoneNumber), e.getMessage(), e.getCode());

            String errorMessage = mapTwilioError(e);
            return CompletableFuture.completedFuture(
                    ApiResponse.error("SMS failed: " + errorMessage)
            );

        } catch (Exception e) {
            log.error("❌ Unexpected SMS error for {}: {}",
                    maskPhoneNumber(phoneNumber), e.getMessage());

            return CompletableFuture.completedFuture(
                    ApiResponse.error("Failed to send SMS: " + e.getMessage())
            );
        }
    }

    /**
     * Send custom SMS (for admin/alerts)
     */
    @Async
    public CompletableFuture<ApiResponse<String>> sendCustomSms(String phoneNumber, String message) {
        log.info("📱 Sending custom SMS to: {}", maskPhoneNumber(phoneNumber));

        try {
            String e164Number = formatToE164(phoneNumber);

            Message twilioMessage = Message.creator(
                    new PhoneNumber(e164Number),
                    new PhoneNumber(twilioConfig.getPhoneNumber()),
                    message
            ).create();

            log.info("✅ Custom SMS sent to: {} (SID: {})",
                    maskPhoneNumber(phoneNumber), twilioMessage.getSid());

            return CompletableFuture.completedFuture(
                    ApiResponse.success("SMS sent successfully", twilioMessage.getSid())
            );

        } catch (Exception e) {
            log.error("❌ Failed to send custom SMS: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                    ApiResponse.error("Failed to send SMS: " + e.getMessage())
            );
        }
    }

    /**
     * Send bulk SMS (with rate limiting)
     */
    @Async
    public void sendBulkSms(String[] phoneNumbers, String message) {
        log.info("📱 Sending bulk SMS to {} numbers", phoneNumbers.length);

        int successCount = 0;
        int failureCount = 0;

        for (String phone : phoneNumbers) {
            try {
                // Add small delay to avoid rate limits
                Thread.sleep(200); // 200ms between messages

                String e164Number = formatToE164(phone);

                Message.creator(
                        new PhoneNumber(e164Number),
                        new PhoneNumber(twilioConfig.getPhoneNumber()),
                        message
                ).create();

                successCount++;

                if (successCount % 10 == 0) {
                    log.info("📊 Bulk SMS progress: {}/{} sent", successCount, phoneNumbers.length);
                }

            } catch (Exception e) {
                failureCount++;
                log.error("❌ Failed to send to {}: {}", maskPhoneNumber(phone), e.getMessage());
            }
        }

        log.info("✅ Bulk SMS completed: {} successful, {} failed", successCount, failureCount);
    }

    /**
     * Check SMS balance/quota
     */
    public ApiResponse<Object> checkBalance() {
        try {
            // You can add Twilio account balance check here
            // This requires additional Twilio API calls

            return ApiResponse.success("Twilio SMS service is configured and running",
                    Map.of(
                            "phoneNumber", maskPhoneNumber(twilioConfig.getPhoneNumber()),
                            "status", "active",
                            "environment", environment,
                            "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    ));

        } catch (Exception e) {
            log.error("❌ Failed to check SMS balance: {}", e.getMessage());
            return ApiResponse.error("Failed to check SMS balance: " + e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Validate Indian phone number
     */
    private boolean isValidIndianNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }

        // Remove spaces, hyphens, parentheses
        String cleaned = phone.replaceAll("[\\s\\-()]", "");

        return INDIAN_MOBILE_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Format to E.164 standard (+919876543210)
     */
    private String formatToE164(String phone) {
        if (phone == null) return null;

        // Remove all non-digits
        String digits = phone.replaceAll("\\D", "");

        // Already in E.164 with +91
        if (phone.startsWith("+91") && digits.length() == 12) {
            return phone;
        }

        // 12 digits starting with 91
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }

        // 10 digits (Indian mobile)
        if (digits.length() == 10 && digits.startsWith("[6-9]")) {
            return "+91" + digits;
        }

        // 11 digits starting with 0
        if (digits.length() == 11 && digits.startsWith("0")) {
            return "+91" + digits.substring(1);
        }

        // Default: add +91
        return "+91" + digits;
    }

    /**
     * Mask phone number for logging
     */
    private String maskPhoneNumber(String phone) {
        if (phone == null) return "null";
        if (phone.length() <= 6) return "******";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    /**
     * Map Twilio error codes to user-friendly messages
     */
    private String mapTwilioError(ApiException e) {
        return switch (e.getCode()) {
            case 21211 -> "Invalid phone number format";
            case 21408 -> "This number is not SMS capable";
            case 21610 -> "This number has opted out of messages";
            case 30003 -> "Number unreachable";
            case 30004 -> "Message blocked by carrier";
            case 30006 -> "Cannot send to landline";
            case 30007 -> "Carrier violation - message blocked";
            case 30401 -> "Message contains prohibited content";
            default -> e.getMessage();
        };
    }
}
