package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.CookieService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication APIs — Signup, Login, Logout, Profile")
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final HttpServletRequest request;

    // =====================================================================
    // SIGNUP FLOW
    // =====================================================================

    @PostMapping("/signup/send-email-otp")
    @Operation(summary = "Step 1: Send Email OTP",
            description = "Send verification OTP to user's email during signup")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Email exists or invalid data")
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<String>> sendEmailOtp(@Valid @RequestBody SignUpRequest signUpRequest) {
        ApiResponse<String> response = authService.sendEmailOtp(signUpRequest);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/verify-email-otp")
    @Operation(summary = "Step 2: Verify Email OTP")
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEmailOtp(
            @RequestParam String email,
            @RequestParam String otp) {
        ApiResponse<Map<String, Object>> response = authService.verifyEmailOtp(email, otp);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/send-mobile-otp")
    @Operation(summary = "Step 3: Send Mobile OTP")
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<String>> sendMobileOtp(
            @RequestParam String email,
            @Parameter(example = "+919876543210") @RequestParam String phoneNumber) {
        ApiResponse<String> response = authService.sendMobileOtp(email, phoneNumber);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/signup/verify-mobile-otp")
    @Operation(summary = "Step 4: Verify Mobile OTP and Complete Registration",
            description = "Creates account, sets auth cookies, and returns user info")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Registration successful — auth cookies set",
                    content = @Content(schema = @Schema(implementation = Map.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid OTP")
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyMobileOtpAndRegister(
            @Valid @RequestBody SignUpRequest signUpRequest,
            @RequestParam String otp) {

        ApiResponse<Map<String, Object>> result = authService.verifyMobileOtpAndRegister(signUpRequest, otp, request);

        if (result.isSuccess()) {
            Map<String, Object> data = result.getData();
            String accessToken  = (String) data.remove("accessToken");
            String refreshToken = (String) data.remove("refreshToken");
            data.remove("tokenType");
            data.remove("deviceId");

            if (accessToken != null && refreshToken != null) {
                HttpHeaders headers = cookieService.buildAuthCookieHeaders(accessToken, refreshToken);
                return ResponseEntity.ok().headers(headers).body(result);
            }
        }

        return ResponseEntity.status(400).body(result);
    }

    // =====================================================================
    // LOGIN FLOW
    // =====================================================================

    @PostMapping("/login")
    @Operation(summary = "Step 1: Login with Email and Password",
            description = "Validates credentials and sends OTP to email and mobile")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account locked")
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest loginRequest) {
        ApiResponse<Map<String, Object>> response = authService.loginWithPassword(loginRequest);
        return ResponseEntity.status(response.isSuccess() ? 200 : 401).body(response);
    }

    @PostMapping("/login/verify-otp")
    @Operation(summary = "Step 2: Verify Login OTP",
            description = "Verifies OTP and completes login. Sets access_token and refresh_token as HttpOnly cookies. " +
                    "Response body contains user info (no tokens). The browser stores cookies automatically.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Login successful — cookies set, user info returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid OTP")
    })
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyLoginOtp(
            @RequestParam String email,
            @Parameter(description = "6-digit OTP from email or mobile") @RequestParam String otp) {

        ApiResponse<Map<String, Object>> result = authService.verifyLoginOtp(email, otp, request);

        if (result.isSuccess()) {
            Map<String, Object> data = result.getData();
            String accessToken  = (String) data.remove("accessToken");
            String refreshToken = (String) data.remove("refreshToken");
            data.remove("tokenType");
            data.remove("deviceId");

            if (accessToken != null && refreshToken != null) {
                HttpHeaders headers = cookieService.buildAuthCookieHeaders(accessToken, refreshToken);
                return ResponseEntity.ok().headers(headers).body(result);
            }
        }

        return ResponseEntity.status(401).body(result);
    }

    // =====================================================================
    // LOGOUT
    // =====================================================================

    @PostMapping("/logout")
    @Operation(summary = "Logout User",
            description = "Revokes tokens, clears access_token and refresh_token cookies")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<String>> logout() {
        ApiResponse<String> response = authService.logoutUser(request);
        return ResponseEntity.ok()
                .headers(cookieService.buildClearCookieHeaders())
                .body(response);
    }

    // =====================================================================
    // CURRENT USER
    // =====================================================================

    @GetMapping("/me")
    @Operation(summary = "Get Current User Profile",
            description = "Returns profile of currently authenticated user (requires valid access_token cookie or Bearer token)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser() {
        return authService.getCurrentUser()
                .map(user -> {
                    Map<String, Object> userInfo = Map.of(
                            "id", user.getId().toString(),
                            "email", user.getEmail(),
                            "firstName", user.getFirstName(),
                            "lastName", user.getLastName(),
                            "role", user.getUserRole().name(),
                            "isEmailVerified", user.getIsEmailVerified(),
                            "isMobileVerified", user.getIsMobileVerified()
                    );
                    return ResponseEntity.ok(ApiResponse.<Map<String, Object>>success("User info", userInfo));
                })
                .orElse(ResponseEntity.status(401).body(ApiResponse.error("Not authenticated")));
    }

    // =====================================================================
    // SIMPLE SIGNUP (Testing Only)
    // =====================================================================

    @PostMapping("/signup/simple")
    @Operation(summary = "Simple Signup (Testing Only)",
            description = "Direct registration without OTP — FOR TESTING ONLY. Sets auth cookies.")
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<Map<String, Object>>> simpleSignup(
            @Valid @RequestBody SignUpRequest signUpRequest) {

        ApiResponse<Map<String, Object>> result = authService.simpleRegister(signUpRequest, request);

        if (result.isSuccess()) {
            Map<String, Object> data = result.getData();
            String accessToken  = (String) data.remove("accessToken");
            String refreshToken = (String) data.remove("refreshToken");
            data.remove("tokenType");
            data.remove("deviceId");

            if (accessToken != null && refreshToken != null) {
                HttpHeaders headers = cookieService.buildAuthCookieHeaders(accessToken, refreshToken);
                return ResponseEntity.ok().headers(headers).body(result);
            }
        }

        return ResponseEntity.status(400).body(result);
    }
}
