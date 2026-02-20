package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.DeviceEntity;
import com.fmt.fmt_backend.entity.RefreshTokenEntity;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.repository.DeviceRepository;
import com.fmt.fmt_backend.repository.RefreshTokenRepository;
import com.fmt.fmt_backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final DeviceService deviceService;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration:21600000}") // 6 hours default
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:1209600000}") // 14 days default
    private long refreshTokenExpiration;

    /**
     * Generate both access token and refresh token for a user
     */
    @Transactional
    public Map<String, Object> generateTokenPair(User user, HttpServletRequest request) {
        log.info("🔑 Generating token pair for user: {}", user.getEmail());

        // NOW WORKS - passing request parameter
        String deviceFingerprint = deviceService.generateDeviceFingerprint(request);

        // Register or update device - also needs request
        DeviceEntity device = deviceService.registerDevice(user, deviceFingerprint, request);

        // Generate access token (JWT)
        String accessToken = generateAccessToken(user, device);

        // Generate refresh token (random UUID)
        String refreshToken = generateRefreshToken(user, device);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        tokens.put("expiresIn", accessTokenExpiration / 1000);
        tokens.put("tokenType", "Bearer");
        tokens.put("deviceId", device.getId());

        return tokens;
    }

    /**
     * Generate JWT access token
     */
    public String generateAccessToken(User user, DeviceEntity device) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("role", user.getUserRole());
        claims.put("deviceId", device.getId());
        claims.put("deviceFingerprint", device.getDeviceFingerprint());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generate refresh token (stored in database)
     */
    @Transactional
    public String generateRefreshToken(User user, DeviceEntity device) {
        // Revoke any existing refresh tokens for this device
        refreshTokenRepository.revokeAllDeviceTokens(device.getId(), LocalDateTime.now());

        // Generate new refresh token
        String tokenValue = UUID.randomUUID().toString();

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .user(user)
                .device(device)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        log.info("✅ Refresh token generated for device: {}", device.getId());
        return tokenValue;
    }

    /**
     * Refresh access token using valid refresh token
     */
    @Transactional
    public Map<String, Object> refreshAccessToken(String refreshTokenValue, String currentDeviceFingerprint) {
        log.info("🔄 Refreshing access token");

        // Find refresh token
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Validate token
        if (!refreshToken.isValid()) {
            log.warn("❌ Invalid or expired refresh token: {}", refreshToken.getId());
            throw new RuntimeException("Refresh token expired or revoked");
        }

        // Validate device fingerprint matches
        if (!refreshToken.getDevice().getDeviceFingerprint().equals(currentDeviceFingerprint)) {
            log.warn("❌ Device fingerprint mismatch for refresh token");
            throw new RuntimeException("Invalid device for refresh token");
        }

        User user = refreshToken.getUser();
        DeviceEntity device = refreshToken.getDevice();

        // Update device last active
        device.setLastActiveAt(LocalDateTime.now());
        deviceRepository.save(device);

        // Generate new access token (keep same refresh token for now)
        String newAccessToken = generateAccessToken(user, device);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", refreshTokenValue); // Same refresh token
        tokens.put("expiresIn", accessTokenExpiration / 1000);
        tokens.put("tokenType", "Bearer");

        log.info("✅ Access token refreshed for user: {}", user.getEmail());

        return tokens;
    }

    /**
     * Rotate refresh token (issue new one, revoke old)
     */
    @Transactional
    public Map<String, Object> rotateRefreshToken(String oldRefreshTokenValue, String currentDeviceFingerprint) {
        log.info("🔄 Rotating refresh token");

        // Find and validate old token
        RefreshTokenEntity oldToken = refreshTokenRepository.findByToken(oldRefreshTokenValue)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (!oldToken.isValid()) {
            throw new RuntimeException("Refresh token expired or revoked");
        }

        if (!oldToken.getDevice().getDeviceFingerprint().equals(currentDeviceFingerprint)) {
            throw new RuntimeException("Invalid device for refresh token");
        }

        // Revoke old token
        oldToken.setRevoked(true);
        oldToken.setRevokedAt(LocalDateTime.now());
        oldToken.setRevokedReason("rotated");
        refreshTokenRepository.save(oldToken);

        // Generate new token pair
        User user = oldToken.getUser();
        DeviceEntity device = oldToken.getDevice();

        // Update device last active
        device.setLastActiveAt(LocalDateTime.now());
        deviceRepository.save(device);

        // Generate new tokens
        String newAccessToken = generateAccessToken(user, device);
        String newRefreshToken = generateRefreshToken(user, device);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", newRefreshToken);
        tokens.put("expiresIn", accessTokenExpiration / 1000);
        tokens.put("tokenType", "Bearer");

        log.info("✅ Refresh token rotated for user: {}", user.getEmail());

        return tokens;
    }

    /**
     * Revoke all refresh tokens for a user (logout from all devices)
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());
        log.info("🔒 All tokens revoked for user: {}", user.getEmail());
    }

    /**
     * Revoke refresh tokens for a specific device
     */
    @Transactional
    public void revokeDeviceTokens(UUID deviceId) {
        refreshTokenRepository.revokeAllDeviceTokens(deviceId, LocalDateTime.now());
        log.info("🔒 Tokens revoked for device: {}", deviceId);
    }

    /**
     * Validate access token and extract claims
     */
    public Claims validateAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("❌ Invalid access token: {}", e.getMessage());
            throw new RuntimeException("Invalid access token");
        }
    }

    /**
     * Extract email from access token
     */
    public String getEmailFromToken(String token) {
        Claims claims = validateAccessToken(token);
        return claims.getSubject();
    }

    /**
     * Extract device ID from access token
     */
    public UUID getDeviceIdFromToken(String token) {
        Claims claims = validateAccessToken(token);
        return UUID.fromString(claims.get("deviceId", String.class));
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = validateAccessToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Cleanup expired and revoked tokens (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("🧹 Starting cleanup of expired refresh tokens");

        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteExpiredAndRevoked(now);

        log.info("✅ Cleanup completed");
    }

    /**
     * Get signing key for JWT
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Get remaining validity of refresh token in seconds
     */
    public long getRefreshTokenValidity(String refreshTokenValue) {
        RefreshTokenEntity token = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        if (!token.isValid()) {
            return 0;
        }

        return java.time.Duration.between(LocalDateTime.now(), token.getExpiresAt()).getSeconds();
    }
}
