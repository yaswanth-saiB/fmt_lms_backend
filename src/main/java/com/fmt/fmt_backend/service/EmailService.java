package com.fmt.fmt_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ResendEmailService resendEmailService;

    public void sendOtpEmail(String to, String otp, int expiryMinutes) {
        resendEmailService.sendOtpEmail(to, otp, expiryMinutes);
    }

    public void sendWelcomeEmail(String to, String firstName, String role) {
        resendEmailService.sendWelcomeEmail(to, firstName, role);
    }
}
