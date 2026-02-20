package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.LoginRequest;
import com.fmt.fmt_backend.dto.SignUpRequest;
import com.fmt.fmt_backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final HttpServletRequest request;

    // ========== SIGNUP FLOW ==========

    @PostMapping("/signup/send-email-otp")
    public ResponseEntity<ApiResponse<String>> sendEmailOtp(
            @Valid @RequestBody SignUpRequest signUpRequest) {

        log.info("📧 Signup step 1 - Send email OTP for: {}", signUpRequest.getEmail());

        ApiResponse<String> response = authService.sendEmailOtp(signUpRequest);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/verify-email-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEmailOtp(
            @RequestParam String email,
            @RequestParam String otp) {

        log.info("📧 Signup step 2 - Verify email OTP for: {}", email);

        ApiResponse<Map<String, Object>> response = authService.verifyEmailOtp(email, otp);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/send-mobile-otp")
    public ResponseEntity<ApiResponse<String>> sendMobileOtp(
            @RequestParam String email,
            @RequestParam String phoneNumber) {

        log.info("📱 Signup step 3 - Send mobile OTP to: {}", phoneNumber);

        ApiResponse<String> response = authService.sendMobileOtp(email, phoneNumber);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/verify-mobile-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyMobileOtpAndRegister(
            @Valid @RequestBody SignUpRequest signUpRequest,
            @RequestParam String otp) {

        log.info("📱 Signup step 4 - Verify mobile OTP and register: {}", signUpRequest.getEmail());

        ApiResponse<Map<String, Object>> response = authService.verifyMobileOtpAndRegister(
                signUpRequest, otp, request
        );

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ========== LOGIN FLOW ==========

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest loginRequest) {

        log.info("🔐 Login step 1 - Password verification for: {}", loginRequest.getEmail());

        ApiResponse<Map<String, Object>> response = authService.loginWithPassword(loginRequest);

        return ResponseEntity.status(response.isSuccess() ? 200 : 401).body(response);
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyLoginOtp(
            @RequestParam String email,
            @RequestParam String otp) {

        log.info("🔐 Login step 2 - OTP verification for: {}", email);

        ApiResponse<Map<String, Object>> response = authService.verifyLoginOtp(email, otp, request);

        return ResponseEntity.status(response.isSuccess() ? 200 : 401).body(response);
    }

    // ========== LOGOUT ==========

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {

        log.info("🚪 Logout request");

        ApiResponse<String> response = authService.logoutUser(request);

        return ResponseEntity.ok(response);
    }

    // ========== CURRENT USER ==========

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser() {

        return authService.getCurrentUser()
                .map(user -> {
                    Map<String, Object> userInfo = Map.of(
                            "id", user.getId(),
                            "email", user.getEmail(),
                            "firstName", user.getFirstName(),
                            "lastName", user.getLastName(),
                            "role", user.getUserRole(),
                            "isEmailVerified", user.getIsEmailVerified(),
                            "isMobileVerified", user.getIsMobileVerified()
                    );
                    return ResponseEntity.ok(ApiResponse.success("User info", userInfo));
                })
                .orElse(ResponseEntity.status(401)
                        .body(ApiResponse.error("Not authenticated")));
    }

    // ========== SIMPLIFIED FLOW (Optional - for testing) ==========

    @PostMapping("/signup/simple")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simpleSignup(
            @Valid @RequestBody SignUpRequest signUpRequest) {

        log.info("📝 Simple signup for: {}", signUpRequest.getEmail());

        // This is a simplified version that directly registers
        // Use this for testing without OTP
        ApiResponse<Map<String, Object>> response = authService.verifyMobileOtpAndRegister(
                signUpRequest, "123456", request
        );

        // ✅ FIXED: Properly wrapped in ResponseEntity
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }
}