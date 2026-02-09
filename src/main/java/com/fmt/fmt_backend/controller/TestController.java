package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.UserRepository;
import com.fmt.fmt_backend.service.CustomUserDetailsService;
import com.fmt.fmt_backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/create-test-user")
    public ResponseEntity<Map<String, Object>> createTestUser() {
        Map<String, Object> response = new HashMap<>();

        // Check if test user already exists
        Optional<User> existingUser = userRepository.findByEmail("test@example.com");

        if (existingUser.isPresent()) {
            response.put("message", "Test user already exists");
            response.put("user_id", existingUser.get().getId());
            response.put("email", existingUser.get().getEmail());
            return ResponseEntity.ok(response);
        }

        // Create test user
        User testUser = User.builder()
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .password(passwordEncoder.encode("Password123!")) // Must meet password requirements
                .userRole(UserRole.STUDENT)
                .isActive(true)
                .isEmailVerified(true)
                .failedLoginAttempts(0)
                .lastPasswordChangeAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(testUser);

        response.put("message", "Test user created successfully");
        response.put("user_id", savedUser.getId());
        response.put("email", savedUser.getEmail());
        response.put("password", "Password123!"); // For testing only!
        response.put("note", "Use this email/password for login tests");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-test-accounts")
    public ResponseEntity<Map<String, Object>> resetTestAccounts() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Reset test@example.com account
            Optional<User> testUser = userRepository.findByEmail("test@example.com");

            if (testUser.isPresent()) {
                User user = testUser.get();
                user.setAccountLockedUntil(null);
                user.setFailedLoginAttempts(0);
                user.setIsActive(true);
                userRepository.save(user);

                response.put("test@example.com", "Account reset successfully");
            } else {
                response.put("test@example.com", "User not found");
            }

            // Reset john.doe@example.com if exists
            Optional<User> johnUser = userRepository.findByEmail("john.doe@example.com");
            if (johnUser.isPresent()) {
                User user = johnUser.get();
                user.setAccountLockedUntil(null);
                user.setFailedLoginAttempts(0);
                user.setIsActive(true);
                userRepository.save(user);

                response.put("john.doe@example.com", "Account reset successfully");
            }

            response.put("message", "All test accounts reset");
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/check-user/{email}")
    public ResponseEntity<Map<String, Object>> checkUserState(@PathVariable String email) {
        Map<String, Object> response = new HashMap<>();

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            response.put("found", false);
            response.put("message", "User not found");
            return ResponseEntity.ok(response);
        }

        User user = userOptional.get();

        response.put("found", true);
        response.put("email", user.getEmail());
        response.put("is_active", user.getIsActive());
        response.put("is_email_verified", user.getIsEmailVerified());
        response.put("failed_login_attempts", user.getFailedLoginAttempts());
        response.put("account_locked_until", user.getAccountLockedUntil());
        response.put("is_locked_now",
                user.getAccountLockedUntil() != null &&
                        user.getAccountLockedUntil().isAfter(LocalDateTime.now()));
        response.put("current_time", LocalDateTime.now());
        response.put("can_login",
                user.getIsActive() &&
                        (user.getAccountLockedUntil() == null ||
                                user.getAccountLockedUntil().isBefore(LocalDateTime.now())));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/debug-security")
    public ResponseEntity<Map<String, Object>> debugSecurity() {
        Map<String, Object> response = new HashMap<>();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        response.put("authentication", authentication != null ? authentication.getClass().getSimpleName() : "NULL");
        response.put("authenticated", authentication != null && authentication.isAuthenticated());
        response.put("principal", authentication != null ? authentication.getPrincipal().toString() : "NULL");
        response.put("authorities", authentication != null ? authentication.getAuthorities().toString() : "NULL");
        response.put("name", authentication != null ? authentication.getName() : "NULL");

        // Check headers
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        response.put("authorization_header", request.getHeader("Authorization"));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/check-auth")
    public ResponseEntity<Map<String, Object>> checkAuth() {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            response.put("authenticated", true);
            response.put("username", auth.getName());
            response.put("authorities", auth.getAuthorities().toString());
            response.put("message", "You are authenticated!");
        } else {
            response.put("authenticated", false);
            response.put("message", "You are NOT authenticated!");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-complete-auth-flow")
    public ResponseEntity<Map<String, Object>> testCompleteAuthFlow() {
        Map<String, Object> response = new HashMap<>();
        List<String> steps = new ArrayList<>();

        try {
            // Step 1: Create test user if not exists
            steps.add("Step 1: Checking/Creating test user...");
            Optional<User> existingUser = userRepository.findByEmail("flowtest@example.com");

            User testUser;
            if (existingUser.isPresent()) {
                testUser = existingUser.get();
                steps.add("✅ Test user already exists");
            } else {
                testUser = User.builder()
                        .firstName("Flow")
                        .lastName("Test")
                        .email("flowtest@example.com")
                        .password(passwordEncoder.encode("FlowTest123!"))
                        .userRole(UserRole.STUDENT)
                        .isActive(true)
                        .isEmailVerified(true)
                        .build();
                userRepository.save(testUser);
                steps.add("✅ Test user created");
            }

            // Step 2: Test login
            steps.add("Step 2: Testing login...");
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken("flowtest@example.com", "FlowTest123!")
            );
            steps.add("✅ Login authentication successful");

            // Step 3: Generate JWT
            steps.add("Step 3: Generating JWT token...");
            UserDetails userDetails = customUserDetailsService.loadUserByUsername("flowtest@example.com");
            String jwtToken = jwtService.generateToken(userDetails);
            steps.add("✅ JWT token generated");

            response.put("jwt_token", jwtToken);
            response.put("token_length", jwtToken.length());

            // Step 4: Extract username from token
            steps.add("Step 4: Validating JWT token...");
            String extractedEmail = jwtService.extractUsername(jwtToken);
            steps.add("✅ JWT validation successful. Email: " + extractedEmail);

            response.put("steps", steps);
            response.put("success", true);
            response.put("message", "Complete auth flow test successful!");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            steps.add("❌ Error: " + e.getMessage());
            response.put("steps", steps);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}