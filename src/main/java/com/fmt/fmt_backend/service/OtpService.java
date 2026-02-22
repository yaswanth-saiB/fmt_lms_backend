package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.OtpEntity;
import com.fmt.fmt_backend.repository.OtpRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpRepository otpRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private HttpServletRequest request;

    @Value("${otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${otp.lockout-minutes:10}")
    private int lockoutMinutes;

    @Value("${otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${app.environment:development}")
    private String environment;

    @Transactional
    public String generateOtp(String identifier, OtpEntity.OtpType type) {
        log.info("Generating OTP for: {} ({})", identifier, type);

        //String clientIp = getClientIp();

        // ✅ BUG 4 FIX: Check IP rate limit
//        LocalDateTime ipWindow = LocalDateTime.now().minusHours(1);
//        long ipAttempts = otpRepository.countByIpAddressAndCreatedAtAfter(clientIp, ipWindow);

//        if (ipAttempts >= 10) {  // Max 10 OTPs per hour per IP
//            log.warn("⚠️ IP rate limit exceeded for IP: {}", clientIp);
//            throw new RuntimeException("Too many OTP requests from this IP. Please try again later.");
//        }

        // Check cooldown
        LocalDateTime cooldownTime = LocalDateTime.now().minusSeconds(resendCooldownSeconds);
        long recentCount = otpRepository.countByIdentifierAndCreatedAtAfter(identifier, cooldownTime);

        if (recentCount >= 1) {
            log.warn("OTP cooldown active for: {}", identifier);
            throw new RuntimeException("Please wait " + resendCooldownSeconds + " seconds before requesting new OTP");
        }

        // Generate OTP
        String otp = generateRandomOtp();


        // Save to database
        OtpEntity otpEntity = new OtpEntity();
        otpEntity.setIdentifier(identifier);
        otpEntity.setOtpCode(otp);
        otpEntity.setType(type);
        otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        //otpEntity.setIpAddress(clientIp);

        otpRepository.save(otpEntity);

        // Mask for logs in production
        String logOtp = "production".equals(environment) ? "***" : otp;
        log.info("Generated OTP for {}: {}", identifier, logOtp);

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

    private String getClientIp() {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
