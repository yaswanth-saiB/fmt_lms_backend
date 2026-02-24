package com.fmt.fmt_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final SendGridEmailService sendGridEmailService;

    public void sendOtpEmail(String to, String otp, int expiryMinutes) {
        sendGridEmailService.sendOtpEmail(to, otp, expiryMinutes);
    }

    public void sendWelcomeEmail(String to, String firstName, String role) {
        sendGridEmailService.sendWelcomeEmail(to, firstName, role);
    }
}
