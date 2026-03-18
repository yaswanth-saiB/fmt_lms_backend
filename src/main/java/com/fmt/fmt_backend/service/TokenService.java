package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.DeviceEntity;
import com.fmt.fmt_backend.entity.RefreshTokenEntity;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.entity.UserSession;
import com.fmt.fmt_backend.repository.DeviceRepository;
import com.fmt.fmt_backend.repository.RefreshTokenRepository;
import com.fmt.fmt_backend.repository.UserRepository;
import com.fmt.fmt_backend.repository.UserSessionRepository;
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
    private final UserSessionRepository userSessionRepository;
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
     * Generate both access token and refresh token for a user.
     * Also creates a UserSession so the sessionId can be tracked.
     *
     * Returned map keys: accessToken, refreshToken, sessionId, expiresIn, tokenType, deviceId
     */
    @Transactional
    public Map<String, Object> generateTokenPair(User user, HttpServletRequest request) {
        log.info("🔑 Generating token pair for user: {}", user.getEmail());

        String deviceFingerprint = deviceService.generateDeviceFingerprint(request);
        DeviceEntity device = deviceService.registerDevice(user, deviceFingerprint, request);

        // Create a new session — sessionId is the identity embedded in the JWT
        UUID sessionId = UUID.randomUUID();
        UserSession session = UserSession.builder()
                .sessionId(sessionId)
                .user(user)
                .device(device)
                .expiresAt(LocalDateTime.now().plusSeconds(accessTokenExpiration / 1000))
                .ipAddress(extractClientIp(request))
                .active(true)
                .build();
        userSessionRepository.save(session);

        String accessToken = generateAccessToken(user, device, sessionId);
        String refreshToken = generateRefreshToken(user, device, sessionId);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        tokens.put("sessionId", sessionId.toString());
        tokens.put("expiresIn", accessTokenExpiration / 1000);
        tokens.put("tokenType", "Bearer");
        tokens.put("deviceId", device.getId().toString());

        return tokens;
    }

    /**
     * Generate JWT access token.
     * Claims include: userId, sessionId, role, deviceId (all as Strings for safe JSON serialisation).
     */
    public String generateAccessToken(User user, DeviceEntity device, UUID sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("sessionId", sessionId.toString());
        claims.put("email", user.getEmail());
        claims.put("role", user.getUserRole().name());
        claims.put("deviceId", device.getId().toString());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generate and persist a refresh token linked to the given session.
     */
    @Transactional
    public String generateRefreshToken(User user, DeviceEntity device, UUID sessionId) {
        // Revoke existing refresh tokens for this device
        refreshTokenRepository.revokeAllDeviceTokens(device.getId(), LocalDateTime.now());

        String tokenValue = UUID.randomUUID().toString();

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .user(user)
                .device(device)
                .sessionId(sessionId)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        log.info("✅ Refresh token generated for device: {}, session: {}", device.getId(), sessionId);
        return tokenValue;
    }

    /**
     * Refresh access token using valid refresh token.
     * Re-uses the SAME sessionId so the session stays continuous.
     */
    @Transactional
    public Map<String, Object> refreshAccessToken(String refreshTokenValue, String currentDeviceFingerprint) {
        log.info("🔄 Refreshing access token");

        RefreshTokenEntity refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            log.warn("❌ Invalid or expired refresh token: {}", refreshToken.getId());
            throw new RuntimeException("Refresh token expired or revoked");
        }

        if (!refreshToken.getDevice().getDeviceFingerprint().equals(currentDeviceFingerprint)) {
            log.warn("❌ Device fingerprint mismatch for refresh token");
            throw new RuntimeException("Invalid device for refresh token");
        }

        User user = refreshToken.getUser();
        DeviceEntity device = refreshToken.getDevice();
        UUID sessionId = refreshToken.getSessionId(); // keep the same sessionId

        device.setLastActiveAt(LocalDateTime.now());
        deviceRepository.save(device);

        // New access token, same refresh token, same session
        String newAccessToken = generateAccessToken(user, device, sessionId);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", refreshTokenValue);
        tokens.put("sessionId", sessionId != null ? sessionId.toString() : null);
        tokens.put("expiresIn", accessTokenExpiration / 1000);
        tokens.put("tokenType", "Bearer");

        log.info("✅ Access token refreshed for user: {}, session: {}", user.getEmail(), sessionId);
        return tokens;
    }

    /**
     * Rotate refresh token — issues a new refresh token (revokes old one).
     * The sessionId is preserved so it stays the same login session.
     */
    @Transactional
    public Map<String, Object> rotateRefreshToken(String oldRefreshTokenValue, String currentDeviceFingerprint) {
        log.info("🔄 Rotating refresh token");

        RefreshTokenEntity oldToken = refreshTokenRepository.findByToken(oldRefreshTokenValue)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (!oldToken.isValid()) {
            throw new RuntimeException("Refresh token expired or revoked");
        }

        if (!oldToken.getDevice().getDeviceFingerprint().equals(currentDeviceFingerprint)) {
            throw new RuntimeException("Invalid device for refresh token");
        }

        UUID sessionId = oldToken.getSessionId(); // preserve session

        oldToken.setRevoked(true);
        oldToken.setRevokedAt(LocalDateTime.now());
        oldToken.setRevokedReason("rotated");
        refreshTokenRepository.save(oldToken);

        User user = oldToken.getUser();
        DeviceEntity device = oldToken.getDevice();

        device.setLastActiveAt(LocalDateTime.now());
        deviceRepository.save(device);

        String newAccessToken = generateAccessToken(user, device, sessionId);
        String newRefreshToken = generateRefreshToken(user, device, sessionId);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", newRefreshToken);
        tokens.put("sessionId", sessionId != null ? sessionId.toString() : null);
        tokens.put("expiresIn", accessTokenExpiration / 1000);
        tokens.put("tokenType", "Bearer");

        log.info("✅ Refresh token rotated for user: {}, session: {}", user.getEmail(), sessionId);
        return tokens;
    }

    /**
     * Revoke all refresh tokens and sessions for a user (logout from all devices)
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());
        userSessionRepository.revokeAllUserSessions(user, LocalDateTime.now());
        log.info("🔒 All tokens and sessions revoked for user: {}", user.getEmail());
    }

    /**
     * Revoke refresh tokens and sessions for a specific device
     */
    @Transactional
    public void revokeDeviceTokens(UUID deviceId) {
        refreshTokenRepository.revokeAllDeviceTokens(deviceId, LocalDateTime.now());
        userSessionRepository.revokeDeviceSessions(deviceId, LocalDateTime.now());
        log.info("🔒 Tokens and sessions revoked for device: {}", deviceId);
    }

    /**
     * Revoke a single session by sessionId (targeted logout)
     */
    @Transactional
    public void revokeSession(UUID sessionId) {
        userSessionRepository.revokeSession(sessionId, LocalDateTime.now());
        log.info("🔒 Session revoked: {}", sessionId);
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
     * Cleanup expired and revoked tokens/sessions (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("🧹 Starting cleanup of expired refresh tokens and sessions");

        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteExpiredAndRevoked(now);
        userSessionRepository.deleteExpiredInactive(now);

        log.info("✅ Cleanup completed");
    }

    // ========== PRIVATE HELPERS ==========

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
