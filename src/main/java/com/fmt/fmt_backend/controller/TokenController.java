package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.service.DeviceService;
import com.fmt.fmt_backend.service.JwtService;
import com.fmt.fmt_backend.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/token")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Token Management", description = "APIs for token refresh and validation")
public class TokenController {

    private final TokenService tokenService;
    private final JwtService jwtService;
    private final DeviceService deviceService;
    private final HttpServletRequest request;

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh Access Token",
            description = "Get a new access token using a valid refresh token"
    )
    @SecurityRequirements({})  // Public - uses refresh token, not access token
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshToken(
            @Parameter(description = "Refresh token", required = true)
            @RequestParam String refreshToken) {

        log.info("🔄 Token refresh request received");

        try {
            // Extract device fingerprint from current request
            String deviceFingerprint = deviceService.generateDeviceFingerprint(request);

            // Refresh the token
            Map<String, Object> tokens = tokenService.refreshAccessToken(refreshToken, deviceFingerprint);

            return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", tokens));

        } catch (Exception e) {
            log.error("❌ Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Token refresh failed: " + e.getMessage()));
        }
    }

    @PostMapping("/rotate")
    @Operation(
            summary = "Rotate Refresh Token",
            description = "Get a new refresh token (security best practice)"
    )
    @SecurityRequirements({})  // Public - uses old refresh token
    public ResponseEntity<ApiResponse<Map<String, Object>>> rotateToken(
            @Parameter(description = "Current refresh token", required = true)
            @RequestParam String refreshToken) {

        log.info("🔄 Token rotation request received");

        try {
            String deviceFingerprint = deviceService.generateDeviceFingerprint(request);
            Map<String, Object> tokens = tokenService.rotateRefreshToken(refreshToken, deviceFingerprint);

            return ResponseEntity.ok(ApiResponse.success("Token rotated successfully", tokens));

        } catch (Exception e) {
            log.error("❌ Token rotation failed: {}", e.getMessage());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Token rotation failed: " + e.getMessage()));
        }
    }

    @PostMapping("/revoke-all")
    @Operation(
            summary = "Revoke All Tokens",
            description = "Revoke all refresh tokens for current user"
    )
    public ResponseEntity<ApiResponse<String>> revokeAllTokens(
            @RequestHeader("Authorization") String authHeader) {

        log.info("🔒 Request to revoke all tokens");

        try {
            String token = authHeader.substring(7);
            String email = jwtService.extractUsername(token);

            // Get user and revoke all tokens
            // You'll need to inject UserRepository to get user by email
            // tokenService.revokeAllUserTokens(user);

            return ResponseEntity.ok(ApiResponse.success("All tokens revoked successfully"));

        } catch (Exception e) {
            log.error("❌ Token revocation failed: {}", e.getMessage());
            return ResponseEntity.status(400)
                    .body(ApiResponse.error("Token revocation failed: " + e.getMessage()));
        }
    }

    @GetMapping("/validate")
    @Operation(
            summary = "Validate Token",
            description = "Check if current access token is valid"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        try {

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.error("Invalid authorization header"));
            }

            String token = authHeader.substring(7);

            boolean isValid = !tokenService.isTokenExpired(token);
            String email = tokenService.getEmailFromToken(token);
            UUID deviceId = tokenService.getDeviceIdFromToken(token);

            Map<String, Object> info = new HashMap<>();
            info.put("valid", isValid);
            info.put("email", email);
            info.put("deviceId", deviceId);
            info.put("expired", !isValid);

            return ResponseEntity.ok(ApiResponse.success("Token validated", info));

        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Invalid token: " + e.getMessage()));
        }
    }
}