package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class JwtTestController {

    private final JwtService jwtService;

    @GetMapping("/generate-test-token")
    public ResponseEntity<Map<String, Object>> generateTestToken() {
        log.info("🔑 Generating test token...");

        // Create a test user
        UserDetails testUser = new User(
                "test@example.com",
                "password",
                Collections.singletonList(() -> "ROLE_STUDENT")
        );

        // Generate token
        String token = jwtService.generateToken(testUser);

        // Extract info from token
        String extractedEmail = jwtService.extractUsername(token);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("token_type", "Bearer");
        response.put("extracted_email", extractedEmail);
        response.put("message", "Copy the token and test with /api/test/validate-token");
        response.put("usage", "Add header: Authorization: Bearer " + token);

        log.info("✅ Generated token for: {}", extractedEmail);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("🔍 Token validation request received");
        log.info("Authorization header: {}", authHeader);

        Map<String, Object> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("❌ No valid Authorization header");
            response.put("success", false);
            response.put("message", "No valid Authorization header found. Format: 'Bearer YOUR_TOKEN'");
            response.put("received_header", authHeader);
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String token = authHeader.substring(7);
            log.info("📝 Token received (first 20 chars): {}...",
                    token.substring(0, Math.min(20, token.length())));

            String username = jwtService.extractUsername(token);

            response.put("success", true);
            response.put("message", "Token is valid!");
            response.put("username", username);
            response.put("token_length", token.length());

            log.info("✅ Token validated for user: {}", username);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Token validation failed: {}", e.getMessage(), e);

            response.put("success", false);
            response.put("message", "Token validation failed: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());

            return ResponseEntity.status(401).body(response);
        }
    }

    @GetMapping("/public-test")
    public ResponseEntity<Map<String, Object>> publicTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is a public endpoint - no authentication needed");
        response.put("timestamp", java.time.LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/protected-test")
    public ResponseEntity<Map<String, Object>> protectedTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is a protected endpoint - you have valid JWT!");
        response.put("timestamp", java.time.LocalDateTime.now());
        response.put("status", "Access granted");
        return ResponseEntity.ok(response);
    }
}