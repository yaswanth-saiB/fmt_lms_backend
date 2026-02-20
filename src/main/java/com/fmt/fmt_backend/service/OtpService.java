package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.OtpEntity;
import com.fmt.fmt_backend.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpRepository otpRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${otp.expiry-minutes}")
    private int expiryMinutes;

    @Value("${otp.max-attempts}")
    private int maxAttempts;

    @Value("${otp.lockout-minutes}")
    private int lockoutMinutes;

    @Value("${otp.resend-cooldown-seconds}")
    private int resendCooldownSeconds;

    @Value("${otp.length}")
    private int otpLength;

    @Value("${app.environment:development}")
    private String environment;

    @Transactional
    public String generateOtp(String identifier, OtpEntity.OtpType type) {
        // Check cooldown
        LocalDateTime cooldownTime = LocalDateTime.now().minusSeconds(resendCooldownSeconds);
        long recentCount = otpRepository.countByIdentifierAndCreatedAtAfter(identifier, cooldownTime);

        if (recentCount >= 1) {
            log.warn("OTP cooldown active for: {}", identifier);
            throw new RuntimeException("Please wait " + resendCooldownSeconds + " seconds before requesting new OTP");
        }

        // Generate OTP
        String otp = generateRandomOtp();

        // Mask for logs in production
        String logOtp = "production".equals(environment) ?
                otp.substring(0, 2) + "***" + otp.substring(otp.length() - 1) : otp;

        log.info("Generated OTP for {} ({}): {}", identifier, type, logOtp);

        // Save to database
        OtpEntity otpEntity = new OtpEntity();
        otpEntity.setIdentifier(identifier);
        otpEntity.setOtpCode(otp);
        otpEntity.setType(type);
        otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));

        otpRepository.save(otpEntity);

        return otp;
    }

    @Transactional
    public boolean verifyOtp(String identifier, String otpCode, OtpEntity.OtpType type) {
        Optional<OtpEntity> otpOpt = otpRepository.findTopByIdentifierAndTypeAndVerifiedFalseOrderByCreatedAtDesc(
                identifier, type);

        if (otpOpt.isEmpty()) {
            log.warn("No active OTP found for: {} ({})", identifier, type);
            return false;
        }

        OtpEntity otp = otpOpt.get();

        // Check if locked
        if (otp.isLocked()) {
            log.warn("OTP attempts locked for: {}", identifier);
            throw new RuntimeException("Too many failed attempts. Please try after " + lockoutMinutes + " minutes");
        }

        // Check expiry
        if (otp.isExpired()) {
            log.warn("OTP expired for: {}", identifier);
            return false;
        }

        // Increment attempts
        otp.setAttempts(otp.getAttempts() + 1);

        // Check if attempts exceeded
        if (otp.getAttempts() >= maxAttempts) {
            otp.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
            otpRepository.save(otp);
            log.warn("OTP locked for {} after {} failed attempts", identifier, maxAttempts);
            throw new RuntimeException("Too many failed attempts. Please try after " + lockoutMinutes + " minutes");
        }

        // Verify OTP
        boolean isValid = otp.getOtpCode().equals(otpCode);

        if (isValid) {
            otp.setVerified(true);
            otp.setVerifiedAt(LocalDateTime.now());
            log.info("OTP verified successfully for: {}", identifier);
        } else {
            log.warn("Invalid OTP attempt for: {}", identifier);
        }

        otpRepository.save(otp);
        return isValid;
    }

    private String generateRandomOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }
}
