package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "Authentication APIs - Signup, Login, Logout, Profile")
public class AuthController {

    private final AuthService authService;
    private final HttpServletRequest request;

    // ========== SIGNUP FLOW ==========
    @PostMapping("/signup/send-email-otp")
    @Operation(
            summary = "Step 1: Send Email OTP",
            description = "Send verification OTP to user's email during signup"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "OTP sent successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "400", description = "Email already exists or invalid data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirements({})  // Public endpoint - no token needed
    public ResponseEntity<ApiResponse<String>> sendEmailOtp(
            @Valid @RequestBody SignUpRequest signUpRequest) {

        log.info("📧 Signup step 1 - Send email OTP for: {}", signUpRequest.getEmail());

        ApiResponse<String> response = authService.sendEmailOtp(signUpRequest);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/verify-email-otp")
    @Operation(
            summary = "Step 2: Verify Email OTP",
            description = "Verify the OTP sent to user's email"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "Email verified successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "400", description = "Invalid or expired OTP")
    })
    @SecurityRequirements({})  // Public endpoint - no token needed
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEmailOtp(
            @Parameter(description = "User email", required = true)
            @RequestParam String email,
            @Parameter(description = "6-digit OTP", required = true)
            @RequestParam String otp) {

        log.info("📧 Signup step 2 - Verify email OTP for: {}", email);

        ApiResponse<Map<String, Object>> response = authService.verifyEmailOtp(email, otp);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/send-mobile-otp")
    @Operation(
            summary = "Step 3: Send Mobile OTP",
            description = "Send verification OTP to user's mobile number"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "OTP sent successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "400", description = "Invalid phone number or email")
    })
    @SecurityRequirements({})  // Public endpoint - no token needed
    public ResponseEntity<ApiResponse<String>> sendMobileOtp(
            @Parameter(description = "User email", required = true)
            @RequestParam String email,
            @Parameter(description = "Mobile number with country code", required = true, example = "+919876543210")
            @RequestParam String phoneNumber) {

        log.info("📱 Signup step 3 - Send mobile OTP to: {}", phoneNumber);

        ApiResponse<String> response = authService.sendMobileOtp(email, phoneNumber);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/verify-mobile-otp")
    @Operation(
            summary = "Step 4: Verify Mobile OTP and Complete Registration",
            description = "Verify mobile OTP and create user account"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "Registration successful",
                    content = @Content(schema = @Schema(implementation = Map.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "400", description = "Invalid OTP or validation failed")
    })
    @SecurityRequirements({})  // Public endpoint - no token needed
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyMobileOtpAndRegister(
            @Valid @RequestBody SignUpRequest signUpRequest,
            @Parameter(description = "6-digit OTP", required = true)
            @RequestParam String otp) {

        log.info("📱 Signup step 4 - Verify mobile OTP and register: {}", signUpRequest.getEmail());

        ApiResponse<Map<String, Object>> response = authService.verifyMobileOtpAndRegister(
                signUpRequest, otp, request
        );

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ========== LOGIN FLOW ==========

    @PostMapping("/login")
    @Operation(
            summary = "Step 1: Login with Email and Password",
            description = "Authenticate with email/password and receive OTP"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "OTP sent to email and mobile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "401", description = "Invalid credentials"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "423", description = "Account locked")
    })
    @SecurityRequirements({})  // Public endpoint - no token needed
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest loginRequest) {

        log.info("🔐 Login step 1 - Password verification for: {}", loginRequest.getEmail());

        ApiResponse<Map<String, Object>> response = authService.loginWithPassword(loginRequest);

        return ResponseEntity.status(response.isSuccess() ? 200 : 401).body(response);
    }

    @PostMapping("/login/verify-otp")
    @Operation(
            summary = "Step 2: Verify Login OTP",
            description = "Verify OTP (email or mobile) and complete login"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "Login successful, returns tokens"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "401", description = "Invalid OTP")
    })
    @SecurityRequirements({})  // Public endpoint - no token needed
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyLoginOtp(
            @Parameter(description = "User email", required = true)
            @RequestParam String email,
            @Parameter(description = "6-digit OTP from email or mobile", required = true)
            @RequestParam String otp) {

        log.info("🔐 Login step 2 - OTP verification for: {}", email);

        ApiResponse<Map<String, Object>> response = authService.verifyLoginOtp(email, otp, request);

        return ResponseEntity.status(response.isSuccess() ? 200 : 401).body(response);
    }

    // ========== LOGOUT ==========

    @PostMapping("/logout")
    @Operation(
            summary = "Logout User",
            description = "Logout current user and revoke tokens"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "Logout successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<String>> logout() {

        log.info("🚪 Logout request");

        ApiResponse<String> response = authService.logoutUser(request);

        return ResponseEntity.ok(response);
    }

    // ========== CURRENT USER ==========

    @GetMapping("/me")
    @Operation(
            summary = "Get Current User Profile",
            description = "Returns profile of currently authenticated user"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "User profile retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "401", description = "Not authenticated")
    })

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
    @Operation(
            summary = "Simple Signup (Testing Only)",
            description = "Direct registration without OTP verification - FOR TESTING ONLY"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "200", description = "Registration successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse
                    (responseCode = "400", description = "Validation failed or email exists")
    })
    @SecurityRequirements({})  // Public endpoint - no token needed
    public ResponseEntity<ApiResponse<Map<String, Object>>> simpleSignup(
            @Valid @RequestBody SignUpRequest signUpRequest) {

        log.info("📝 Simple signup for: {}", signUpRequest.getEmail());

        // This is a simplified version that directly registers
        // Use this for testing without OTP
        // Use the NEW simpleRegister method
        ApiResponse<Map<String, Object>> response = authService.simpleRegister(signUpRequest, request);

        // ✅ FIXED: Properly wrapped in ResponseEntity
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }
}