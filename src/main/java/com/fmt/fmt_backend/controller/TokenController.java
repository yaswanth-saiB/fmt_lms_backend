package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.repository.UserRepository;
import com.fmt.fmt_backend.service.CookieService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/token")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Token Management", description = "APIs for token refresh, rotation, and validation")
public class TokenController {

    private final TokenService tokenService;
    private final JwtService jwtService;
    private final DeviceService deviceService;
    private final CookieService cookieService;
    private final UserRepository userRepository;
    private final HttpServletRequest request;

    // =====================================================================
    // REFRESH — browser sends refresh_token cookie automatically
    //           (or pass as query param when testing via Swagger)
    // =====================================================================

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh Access Token",
            description = "Issues a new access_token cookie using the refresh_token cookie. " +
                    "The browser sends the cookie automatically (path=/api/auth/token). " +
                    "For Swagger testing you can also pass the token as a query param."
    )
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshToken(
            @Parameter(description = "Refresh token (Swagger fallback — cookie is preferred)")
            @RequestParam(required = false) String refreshToken) {

        log.info("🔄 Token refresh request");

        try {
            // Cookie first, query param as Swagger fallback
            String token = cookieService.getRefreshTokenFromCookies(request)
                    .orElse(refreshToken);

            if (token == null || token.isBlank()) {
                return ResponseEntity.status(401).body(ApiResponse.error("Refresh token not found"));
            }

            String deviceFingerprint = deviceService.generateDeviceFingerprint(request);
            Map<String, Object> tokens = tokenService.refreshAccessToken(token, deviceFingerprint);

            // Update access_token cookie, keep same refresh_token cookie
            String newAccessToken = (String) tokens.get("accessToken");
            HttpHeaders headers = cookieService.buildAuthCookieHeaders(newAccessToken, token);

            // Return only non-sensitive info
            Map<String, Object> body = new HashMap<>();
            body.put("sessionId", tokens.get("sessionId"));
            body.put("expiresIn", tokens.get("expiresIn"));

            return ResponseEntity.ok().headers(headers).body(ApiResponse.success("Token refreshed successfully", body));

        } catch (Exception e) {
            log.error("❌ Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(ApiResponse.error("Token refresh failed: " + e.getMessage()));
        }
    }

    @PostMapping("/rotate")
    @Operation(
            summary = "Rotate Refresh Token",
            description = "Issues a new refresh_token (and access_token). Revokes the old refresh_token. " +
                    "Use this for extended sessions / sliding-window expiry."
    )
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<Map<String, Object>>> rotateToken(
            @Parameter(description = "Current refresh token (Swagger fallback — cookie is preferred)")
            @RequestParam(required = false) String refreshToken) {

        log.info("🔄 Token rotation request");

        try {
            String token = cookieService.getRefreshTokenFromCookies(request)
                    .orElse(refreshToken);

            if (token == null || token.isBlank()) {
                return ResponseEntity.status(401).body(ApiResponse.error("Refresh token not found"));
            }

            String deviceFingerprint = deviceService.generateDeviceFingerprint(request);
            Map<String, Object> tokens = tokenService.rotateRefreshToken(token, deviceFingerprint);

            String newAccessToken = (String) tokens.get("accessToken");
            String newRefreshToken = (String) tokens.get("refreshToken");
            HttpHeaders headers = cookieService.buildAuthCookieHeaders(newAccessToken, newRefreshToken);

            Map<String, Object> body = new HashMap<>();
            body.put("sessionId", tokens.get("sessionId"));
            body.put("expiresIn", tokens.get("expiresIn"));

            return ResponseEntity.ok().headers(headers).body(ApiResponse.success("Token rotated successfully", body));

        } catch (Exception e) {
            log.error("❌ Token rotation failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(ApiResponse.error("Token rotation failed: " + e.getMessage()));
        }
    }

    // =====================================================================
    // VALIDATE — useful for debugging / health checks
    // =====================================================================

    @GetMapping("/validate")
    @Operation(
            summary = "Validate Current Token",
            description = "Checks if the current access_token cookie (or Bearer header) is valid. " +
                    "Returns userId, sessionId, role, and deviceId from the token claims."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken() {

        try {
            // Try cookie, then header
            String token = cookieService.getAccessTokenFromCookies(request).orElse(null);
            if (token == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("No token found"));
            }

            boolean expired = tokenService.isTokenExpired(token);
            String email = tokenService.getEmailFromToken(token);
            UUID deviceId = tokenService.getDeviceIdFromToken(token);
            UUID sessionId = jwtService.extractSessionId(token);
            UUID userId = jwtService.extractUserId(token);
            String role = jwtService.extractRole(token);

            Map<String, Object> info = new HashMap<>();
            info.put("valid", !expired);
            info.put("email", email);
            info.put("userId", userId != null ? userId.toString() : null);
            info.put("sessionId", sessionId != null ? sessionId.toString() : null);
            info.put("role", role);
            info.put("deviceId", deviceId != null ? deviceId.toString() : null);
            info.put("expired", expired);

            return ResponseEntity.ok(ApiResponse.success("Token validated", info));

        } catch (Exception e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid token: " + e.getMessage()));
        }
    }

    // =====================================================================
    // REVOKE ALL
    // =====================================================================

    @PostMapping("/revoke-all")
    @Operation(
            summary = "Revoke All Tokens (Logout from All Devices)",
            description = "Revokes all refresh tokens and sessions for the current user, then clears cookies."
    )
    public ResponseEntity<ApiResponse<String>> revokeAllTokens(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            // Get token from cookie or header
            String token = cookieService.getAccessTokenFromCookies(request).orElse(null);
            if (token == null && authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            if (token == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
            }

            UUID userId = jwtService.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("Invalid token: missing userId claim"));
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            tokenService.revokeAllUserTokens(user);

            HttpHeaders headers = cookieService.buildClearCookieHeaders();
            return ResponseEntity.ok().headers(headers)
                    .body(ApiResponse.success("All sessions revoked. You have been logged out from all devices."));

        } catch (Exception e) {
            log.error("❌ Token revocation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(ApiResponse.error("Revocation failed: " + e.getMessage()));
        }
    }
}
