package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.AuthResponse;
import com.fmt.fmt_backend.dto.LoginRequest;
import com.fmt.fmt_backend.dto.SignUpRequest;
import com.fmt.fmt_backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<String>> signup(
            @Valid @RequestBody SignUpRequest signUpRequest) {

        log.info("📝 Signup request for: {} {}",
                signUpRequest.getFirstName(), signUpRequest.getLastName());

        ApiResponse<String> response = authService.registerUser(signUpRequest);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest) {

        log.info("🔐 Login request for: {}", loginRequest.getEmail());

        ApiResponse<AuthResponse> response = authService.loginUser(loginRequest);

        return ResponseEntity.status(response.isSuccess() ? 200 : 401)
                .body(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(
            @RequestParam String token) {

        log.info("📧 Email verification request");

        ApiResponse<String> response = authService.verifyEmail(token);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {

        log.info("🚪 Logout request");

        ApiResponse<String> response = authService.logoutUser();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<String>> getCurrentUser() {
        // This will be protected by JWT filter
        return ResponseEntity.ok(ApiResponse.success(
                "Current user endpoint - protected by JWT",
                "You have valid authentication!"
        ));
    }

}
