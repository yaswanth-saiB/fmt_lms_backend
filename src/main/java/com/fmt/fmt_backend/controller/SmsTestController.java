package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.service.OtpService;
import com.fmt.fmt_backend.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/test/sms")
@RequiredArgsConstructor
@Slf4j
public class SmsTestController {

    private final SmsService smsService;
    private final OtpService otpService;

    @PostMapping("/send-otp")
    public CompletableFuture<ResponseEntity<ApiResponse<Map<String, Object>>>> sendOtp(
            @RequestParam String phone) {

        log.info("📱 Test OTP SMS request for: {}", phone);

        return smsService.sendOtpSms(phone, "123456")
                .thenApply(response -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("phone", phone);
                    data.put("otp", "123456");
                    data.put("note", "This is a test OTP. In production, OTP will be generated dynamically.");

                    return ResponseEntity.ok(ApiResponse.success("Test OTP sent", data));
                });
    }

    @PostMapping("/send-custom")
    public CompletableFuture<ResponseEntity<ApiResponse<Map<String, Object>>>> sendCustom(
            @RequestParam String phone,
            @RequestParam String message) {

        log.info("📱 Test custom SMS request for: {}", phone);

        return smsService.sendCustomSms(phone, message)
                .thenApply(response -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("phone", phone);
                    data.put("message", message);

                    return ResponseEntity.ok(ApiResponse.success("Custom SMS sent", data));
                });
    }

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validatePhone(
            @RequestParam String phone) {

        Map<String, Object> result = new HashMap<>();
        result.put("original", phone);

        // Clean phone
        String cleaned = phone.replaceAll("[\\s\\-()]", "");
        result.put("cleaned", cleaned);

        // Check if valid Indian number
        boolean isValid = phone != null && phone.matches("^(\\+91|91|0)?[6-9]\\d{9}$");
        result.put("isValidIndian", isValid);

        // Format to E.164
        if (isValid) {
            String e164 = formatToE164(phone);
            result.put("e164Format", e164);
        }

        return ResponseEntity.ok(ApiResponse.success("Phone validation", result));
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<Object>> checkBalance() {
        return ResponseEntity.ok(smsService.checkBalance());
    }

    private String formatToE164(String phone) {
        String digits = phone.replaceAll("\\D", "");

        if (digits.length() == 10) {
            return "+91" + digits;
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            return "+91" + digits.substring(1);
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        return phone;
    }
}