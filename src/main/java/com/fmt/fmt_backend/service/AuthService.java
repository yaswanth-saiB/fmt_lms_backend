package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.AuthResponse;
import com.fmt.fmt_backend.dto.LoginRequest;
import com.fmt.fmt_backend.dto.SignUpRequest;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

    @Transactional
    public ApiResponse<String> registerUser(SignUpRequest signUpRequest) {
        log.info("📝 Registration attempt for email: {}", signUpRequest.getEmail());

        // 1. Validate email doesn't exist
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            log.warn("❌ Registration failed: Email already exists - {}", signUpRequest.getEmail());
            return ApiResponse.error("Email is already registered");
        }

        // 2. Create user entity
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
                .userRole(UserRole.STUDENT) // Default role
                .isActive(true) // Auto-activate for now
                .isEmailVerified(false)
                .isMobileVerified(false)
                .failedLoginAttempts(0)
                .lastPasswordChangeAt(LocalDateTime.now())
                .build();

        // 3. Save user to database
        User savedUser = userRepository.save(user);
        log.info("✅ User registered successfully: {} (ID: {})",
                user.getEmail(), savedUser.getId());

        return ApiResponse.success(
                "Registration successful! You can now login.",
                savedUser.getId().toString()
        );
    }

    @Transactional
    public ApiResponse<AuthResponse> loginUser(LoginRequest loginRequest) {
        log.info("🔐 Login attempt for email: {}", loginRequest.getEmail());

        String email = loginRequest.getEmail().toLowerCase().trim();

        try {
            // 1. Check if user exists
            Optional<User> userOptional = userRepository.findByEmail(email);
            if (userOptional.isEmpty()) {
                log.warn("❌ Login failed: User not found - {}", email);
                return ApiResponse.error("Invalid email or password");
            }

            User user = userOptional.get();

            // 2. Check if account is locked
            if (user.getAccountLockedUntil() != null &&
                    user.getAccountLockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("🔒 Login failed: Account locked - {}", email);
                return ApiResponse.error("Account is temporarily locked. Please try again later.");
            }


            // 3. Authenticate with Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, loginRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 4. Reset failed attempts on successful login
            userRepository.resetFailedAttempts(email);

            // 5. Update last login
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            // 6. Get UserDetails from CustomUserDetailsService (FIXED)
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

            // 7. Generate JWT token using UserDetails (FIXED)
            String jwtToken = jwtService.generateToken(userDetails);

            // 8. Create response
            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(jwtToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtService.getExpirationInSeconds())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .userRole(user.getUserRole().name())
                    .isEmailVerified(user.getIsEmailVerified())
                    .lastLoginAt(user.getLastLoginAt())
                    .build();

            log.info("✅ Login successful for: {}", email);

            return ApiResponse.success("Login successful", authResponse);

        } catch (BadCredentialsException e) {
            // Increment failed attempts
            userRepository.incrementFailedAttempts(email);

            // Check if should lock account (after 5 failed attempts)
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isPresent() && user.get().getFailedLoginAttempts() >= 5) {
                userRepository.lockAccount(email, LocalDateTime.now().plusMinutes(15));
                log.warn("🔒 Account locked after 5 failed attempts: {}", email);
                return ApiResponse.error("Too many failed attempts. Account locked for 15 minutes.");
            }

            log.warn("❌ Login failed - Invalid credentials for: {}", email);
            return ApiResponse.error("Invalid email or password");

        } catch (Exception e) {
            log.error("💥 Login error for {}: {}", email, e.getMessage(), e);
            return ApiResponse.error("Login failed: " + e.getMessage());
        }
    }

    public ApiResponse<String> verifyEmail(String token) {
        log.info("📧 Email verification attempt with token: {}", token);

        // TODO: Implement actual email verification logic
        // For now, return success message

        return ApiResponse.success("Email verification endpoint ready. Token received: " + token);
    }

    @Transactional
    public ApiResponse<String> logoutUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            log.info("🚪 Logout successful for: {}", email);

            // Clear security context
            SecurityContextHolder.clearContext();

            return ApiResponse.success("Logout successful");
        }

        return ApiResponse.error("No user is currently logged in");
    }

    // Helper method to get current authenticated user
    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            return userRepository.findByEmail(email);
        }

        return Optional.empty();
    }
}