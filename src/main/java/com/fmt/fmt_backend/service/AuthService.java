package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.LoginRequest;
import com.fmt.fmt_backend.dto.SignUpRequest;
import com.fmt.fmt_backend.entity.OtpEntity;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.OtpRepository;
import com.fmt.fmt_backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final EmailService emailService;
    private final DeviceService deviceService;
    private final TokenService tokenService;
    private final CustomUserDetailsService customUserDetailsService;

    // ========== SIGNUP FLOW WITH OTP ==========

    /**
     * Step 1: Send email OTP for verification
     */
    @Transactional
    public ApiResponse<String> sendEmailOtp(SignUpRequest signUpRequest) {
        log.info("📧 Step 1 - Sending email OTP to: {}", signUpRequest.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            log.warn("❌ Email already exists: {}", signUpRequest.getEmail());
            return ApiResponse.error("Email is already registered");
        }


        try {
            // Generate and send OTP via email
            String otp = otpService.generateOtp(
                    signUpRequest.getEmail(),
                    OtpEntity.OtpType.EMAIL_VERIFICATION
            );

            // Send email (async)
            emailService.sendOtpEmail(signUpRequest.getEmail(), otp, 5);

            // Store signup data temporarily (you might want to use a cache)
            // For now, we'll just return success

            return ApiResponse.success(
                    "OTP sent to your email. Please verify to continue.",
                    signUpRequest.getEmail()
            );

        } catch (Exception e) {
            log.error("❌ Failed to send email OTP: {}", e.getMessage());
            return ApiResponse.error("Failed to send OTP. Please try again.");
        }
    }

    /**
     * Step 2: Verify email OTP
     */
    @Transactional
    public ApiResponse<Map<String, Object>> verifyEmailOtp(String email, String otpCode) {
        log.info("📧 Step 2 - Verifying email OTP for: {}", email);

        boolean isValid = otpService.verifyOtp(email, otpCode, OtpEntity.OtpType.EMAIL_VERIFICATION);

        if (!isValid) {
            return ApiResponse.error("Invalid or expired OTP");
        }

        return ApiResponse.success(
                "Email verified successfully. Please provide mobile number for verification.",
                Map.of("email", email, "emailVerified", true)
        );
    }

    /**
     * Step 3: Send mobile OTP — DISABLED (SMS service removed)
     */
    public ApiResponse<String> sendMobileOtp(String email, String phoneNumber) {
        return ApiResponse.error("SMS verification is no longer available. Please complete registration via email OTP.");
    }

    /**
     * Step 4: Complete registration after email OTP is verified (mobile OTP removed)
     */
    @Transactional
    public ApiResponse<Map<String, Object>> verifyMobileOtpAndRegister(
            SignUpRequest signUpRequest,
            String otpCode,
            HttpServletRequest request) {

        log.info("📝 Step 4 - Completing registration for: {}", signUpRequest.getEmail());

        // Double check email doesn't exist (race condition)
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ApiResponse.error("Email already registered");
        }

        try {
            // Create user
            User user = User.builder()
                    .firstName(signUpRequest.getFirstName())
                    .lastName(signUpRequest.getLastName())
                    .email(signUpRequest.getEmail().toLowerCase().trim())
                    .password(passwordEncoder.encode(signUpRequest.getPassword()))
                    .phoneNumber(signUpRequest.getPhoneNumber())
                    .gender(signUpRequest.getGender())
                    .city(signUpRequest.getCity())
                    .state(signUpRequest.getState())
                    .country(signUpRequest.getCountry())
                    .postalCode(signUpRequest.getPostalCode())
                    .userRole(UserRole.STUDENT)
                    .isActive(true)
                    .isEmailVerified(true)
                    .emailVerifiedAt(LocalDateTime.now())
                    .isMobileVerified(false)
                    .failedLoginAttempts(0)
                    .lastPasswordChangeAt(LocalDateTime.now())
                    .build();

            User savedUser = userRepository.save(user);
            log.info("✅ User registered successfully: {}", savedUser.getEmail());

            // Send welcome email
            emailService.sendWelcomeEmail(
                    savedUser.getEmail(),
                    savedUser.getFirstName(),
                    savedUser.getUserRole().name()
            );

            // Generate tokens for auto-login
            Map<String, Object> tokens = tokenService.generateTokenPair(savedUser, request);

            Map<String, Object> response = new HashMap<>(tokens);
            response.put("userId", savedUser.getId().toString());
            response.put("email", savedUser.getEmail());
            response.put("firstName", savedUser.getFirstName());
            response.put("lastName", savedUser.getLastName());
            response.put("role", savedUser.getUserRole().name());

            return ApiResponse.success("Registration successful! Welcome to First Million Trade.", response);

        } catch (Exception e) {
            log.error("❌ Registration failed: {}", e.getMessage());
            return ApiResponse.error("Registration failed: " + e.getMessage());
        }
    }

    // ========== LOGIN FLOW WITH DUAL OTP ==========

    /**
     * Step 1: Login with email/password
     */
    @Transactional
    public ApiResponse<Map<String, Object>> loginWithPassword(LoginRequest loginRequest) {
        log.info("🔐 Step 1 - Login attempt for: {}", loginRequest.getEmail());

        String email = loginRequest.getEmail().toLowerCase().trim();

        try {
            // Check if user exists
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

            // Check if account is locked
            if (user.getAccountLockedUntil() != null &&
                    user.getAccountLockedUntil().isAfter(LocalDateTime.now())) {
                return ApiResponse.error("Account is temporarily locked. Please try again later.");
            }

            // Authenticate with Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, loginRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Reset failed attempts
            userRepository.resetFailedAttempts(email);

            // Send email OTP
            String emailOtp = otpService.generateOtp(email, OtpEntity.OtpType.LOGIN);
            emailService.sendOtpEmail(email, emailOtp, 5);

            log.info("✅ Login OTP sent to {} (email)", email);

            String otpMessage = "OTP sent to your email. Enter it to login.";

            return ApiResponse.success(
                    otpMessage,
                    Map.of(
                            "email", email,
                            "requiresOtp", true,
                            "expiryMinutes", 5
                    )
            );

        } catch (BadCredentialsException e) {
            // Handle failed login
            handleFailedLogin(email);
            return ApiResponse.error("Invalid email or password");
        }
    }

    /**
     * Step 2: Verify OTP (email or mobile) and complete login
     */
    @Transactional
    public ApiResponse<Map<String, Object>> verifyLoginOtp(
            String email,
            String otpCode,
            HttpServletRequest request) {

        log.info("🔐 Step 2 - Verifying login OTP for: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check account is active before completing login
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return ApiResponse.error("Account is deactivated. Please contact support.");
        }

        // Verify email OTP
        boolean isEmailOtpValid = otpService.verifyOtp(email, otpCode, OtpEntity.OtpType.LOGIN);
        if (!isEmailOtpValid) {
            return ApiResponse.error("Invalid OTP");
        }
        log.info("✅ Email OTP verified for: {}", email);

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(deviceService.generateDeviceFingerprint(request));
        userRepository.save(user);

        // Generate tokens with device info
        Map<String, Object> tokens = tokenService.generateTokenPair(user, request);

        // Build response — controller will extract tokens for cookies and strip them from body
        Map<String, Object> responseData = new HashMap<>(tokens);
        responseData.put("userId", user.getId().toString());
        responseData.put("email", user.getEmail());
        responseData.put("firstName", user.getFirstName());
        responseData.put("lastName", user.getLastName());
        responseData.put("role", user.getUserRole().name());
        responseData.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));

        return ApiResponse.success("Login successful", responseData);
    }

    // ========== LOGOUT ==========

    @Transactional
    public ApiResponse<String> logoutUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();

            User user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                // Get current device fingerprint
                String fingerprint = deviceService.generateDeviceFingerprint(request);

                // Find and revoke tokens for this device
                deviceService.getCurrentDevice(user, request).ifPresent(device -> {
                    tokenService.revokeDeviceTokens(device.getId());
                });
            }

            SecurityContextHolder.clearContext();
            log.info("🚪 Logout successful for: {}", email);

            return ApiResponse.success("Logout successful");
        }

        return ApiResponse.error("No user is currently logged in");
    }

    // ========== GET CURRENT USER ==========

    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() &&
                !(authentication.getPrincipal() instanceof String &&
                        authentication.getPrincipal().equals("anonymousUser"))) {

            String email = authentication.getName();
            return userRepository.findByEmail(email);
        }

        return Optional.empty();
    }

    // ========== CHANGE PASSWORD ==========

    public void changePassword(String currentPassword, String newPassword) {
        User user = getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setLastPasswordChangeAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
    }

    // ========== HELPER METHODS ==========

    private void handleFailedLogin(String email) {
        userRepository.incrementFailedAttempts(email);

        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getFailedLoginAttempts() >= 5) {
                userRepository.lockAccount(email, LocalDateTime.now().plusMinutes(15));
                log.warn("🔒 Account locked after 5 failed attempts: {}", email);
            }
        });
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null) return "null";
        if (phone.length() <= 6) return "******";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    /**
     * SIMPLE signup - NO OTP verification (FOR TESTING ONLY)
     */
    @Transactional
    public ApiResponse<Map<String, Object>> simpleRegister(SignUpRequest signUpRequest, HttpServletRequest request) {

        // Check if email already exists
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ApiResponse.error("Email is already registered");
        }

        // Create user directly - NO OTP verification
        User user = User.builder()
                .firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .email(signUpRequest.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                .phoneNumber(signUpRequest.getPhoneNumber())
                .gender(signUpRequest.getGender())
                .city(signUpRequest.getCity())
                .state(signUpRequest.getState())
                .country(signUpRequest.getCountry())
                .postalCode(signUpRequest.getPostalCode())
                .userRole(UserRole.STUDENT)
                .isActive(true)
                .isEmailVerified(true)  // Auto-verified for simple signup
                .emailVerifiedAt(LocalDateTime.now())
                .isMobileVerified(true)  // Auto-verified for simple signup
                .mobileVerifiedAt(LocalDateTime.now())
                .failedLoginAttempts(0)
                .lastPasswordChangeAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        log.info("✅ SIMPLE signup successful: {}", savedUser.getEmail());

        // Send welcome email (optional)
        emailService.sendWelcomeEmail(
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getUserRole().name()
        );

        // Generate tokens
        Map<String, Object> tokens = tokenService.generateTokenPair(savedUser, request);

        Map<String, Object> response = new HashMap<>(tokens);
        response.put("userId", savedUser.getId().toString());
        response.put("email", savedUser.getEmail());
        response.put("firstName", savedUser.getFirstName());
        response.put("lastName", savedUser.getLastName());
        response.put("role", savedUser.getUserRole().name());

        return ApiResponse.success("Simple signup successful", response);
    }
}