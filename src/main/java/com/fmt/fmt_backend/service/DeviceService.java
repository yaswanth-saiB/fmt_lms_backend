package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.DeviceEntity;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.repository.DeviceRepository;
import com.fmt.fmt_backend.repository.RefreshTokenRepository;
import com.fmt.fmt_backend.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;

    @Value("${device.max-sessions-per-user:2}")
    private int maxSessionsPerUser;

    @Value("${device.max-streaming-sessions:1}")
    private int maxStreamingSessions;

    // =========================================================================
    // FINGERPRINT
    // =========================================================================

    /**
     * Generate a stable device fingerprint from the request.
     *
     * Uses UUID v3 (MD5-based) over "userAgent|ip" so the same browser on the
     * same IP always maps to the same fingerprint.  StandardCharsets.UTF_8 is
     * used explicitly to guarantee identical bytes regardless of JVM locale.
     *
     * Note (intentional): the fingerprint changes when the user's IP changes
     * (VPN, mobile network switch) — this is by design and treated as a new device.
     */
    public String generateDeviceFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ip        = getClientIp(request);

        String raw = (userAgent != null ? userAgent.trim() : "unknown") + "|" + ip;

        // UUID.nameUUIDFromBytes uses MD5 internally (UUID v3) — deterministic
        String fingerprint = UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();

        log.debug("🖐️ Fingerprint: {} for IP: {}", fingerprint, maskIpAddress(ip));
        return fingerprint;
    }

    // =========================================================================
    // REGISTER DEVICE
    // =========================================================================

    /**
     * Register or update a device for the user.
     * After saving the new device, enforces the per-user device limit by
     * auto-revoking the oldest excess device(s).
     */
    @Transactional
    public DeviceEntity registerDevice(User user, String deviceFingerprint, HttpServletRequest request) {
        log.info("📱 Registering device for user: {}", user.getEmail());

        Optional<DeviceEntity> existingOpt = deviceRepository
                .findByUserAndDeviceFingerprint(user, deviceFingerprint);

        if (existingOpt.isPresent()) {
            DeviceEntity existing = existingOpt.get();
            existing.setActive(true);
            existing.setLastActiveAt(LocalDateTime.now());
            existing.setIpAddress(getClientIp(request));
            existing.setUserAgent(request.getHeader("User-Agent"));
            log.info("🔄 Existing device updated: {}", existing.getId());
            return deviceRepository.save(existing);
        }

        // New device
        DeviceEntity newDevice = new DeviceEntity();
        newDevice.setUser(user);
        newDevice.setDeviceFingerprint(deviceFingerprint);
        newDevice.setIpAddress(getClientIp(request));
        newDevice.setUserAgent(request.getHeader("User-Agent"));
        newDevice.setDeviceName(generateDeviceName(request));
        newDevice.setLastActiveAt(LocalDateTime.now());
        newDevice.setFirstSeenAt(LocalDateTime.now());
        newDevice.setActive(true);
        newDevice.setStreaming(false);

        DeviceEntity saved = deviceRepository.save(newDevice);
        log.info("✅ New device registered for {}: {}", user.getEmail(), saved.getDeviceName());

        // Enforce limit — revoke oldest excess devices
        enforceDeviceLimit(user, saved);

        return saved;
    }

    // =========================================================================
    // GET DEVICES
    // =========================================================================

    public List<Map<String, Object>> getUserDevices(User user, HttpServletRequest request) {
        List<DeviceEntity> devices = deviceRepository.findByUserAndIsActiveTrue(user);

        return devices.stream().map(device -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("deviceId",        device.getId());
            dto.put("deviceName",      device.getDeviceName());
            dto.put("ipAddress",       maskIpAddress(device.getIpAddress()));
            dto.put("lastActive",      device.getLastActiveAt());
            dto.put("firstSeen",       device.getFirstSeenAt());
            dto.put("isStreaming",     device.isStreaming());
            dto.put("userAgent",       truncateUserAgent(device.getUserAgent()));
            dto.put("isCurrentDevice", isCurrentDevice(device, request));
            return dto;
        }).collect(Collectors.toList());
    }

    // =========================================================================
    // REVOKE DEVICE
    // =========================================================================

    /**
     * Revoke a specific device: marks it inactive and revokes both its
     * refresh tokens and user sessions so the user is fully logged out
     * on that device.
     */
    @Transactional
    public ApiResponse<String> revokeDevice(User user, UUID deviceId) {
        log.info("🔒 Revoking device {} for user: {}", deviceId, user.getEmail());

        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            return ApiResponse.error("Device not found");
        }

        DeviceEntity device = deviceOpt.get();

        if (!device.getUser().getId().equals(user.getId())) {
            log.warn("❌ Unauthorized device revocation attempt by {}", user.getEmail());
            return ApiResponse.error("Device not found");
        }

        device.setActive(false);
        device.setStreaming(false);
        deviceRepository.save(device);

        // Revoke refresh tokens AND user sessions for this device
        refreshTokenRepository.revokeAllDeviceTokens(deviceId, LocalDateTime.now());
        userSessionRepository.revokeDeviceSessions(deviceId, LocalDateTime.now());

        log.info("✅ Device revoked (tokens + sessions cleared): {}", deviceId);
        return ApiResponse.success("Device revoked successfully");
    }

    // =========================================================================
    // DEVICE LIMIT
    // =========================================================================

    /**
     * Returns devices the user can choose to disconnect when over limit.
     * Excludes the current device from the list.
     */
    public ApiResponse<List<Map<String, Object>>> handleDeviceLimit(User user, HttpServletRequest request) {
        long activeCount = deviceRepository.countByUserAndIsActiveTrue(user);

        if (activeCount <= maxSessionsPerUser) {
            return ApiResponse.success("Within device limit", null);
        }

        List<DeviceEntity> allActive = deviceRepository.findByUserAndIsActiveTrue(user);
        allActive.sort(Comparator.comparing(DeviceEntity::getLastActiveAt)); // oldest first

        Optional<DeviceEntity> currentOpt = getCurrentDevice(user, request);

        List<Map<String, Object>> options = allActive.stream()
                .filter(d -> currentOpt.isEmpty() || !d.getId().equals(currentOpt.get().getId()))
                .limit(activeCount - maxSessionsPerUser + 1)
                .map(device -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("deviceId",   device.getId());
                    dto.put("deviceName", device.getDeviceName());
                    dto.put("lastActive", device.getLastActiveAt());
                    dto.put("ipAddress",  maskIpAddress(device.getIpAddress()));
                    return dto;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(
                String.format("You have %d active devices. Maximum allowed is %d.",
                        activeCount, maxSessionsPerUser),
                options
        );
    }

    /**
     * Disconnect a device the user has chosen (user-initiated).
     */
    @Transactional
    public ApiResponse<String> disconnectDevice(User user, UUID deviceId) {
        log.info("🔌 User {} disconnecting device: {}", user.getEmail(), deviceId);

        ApiResponse<String> revokeResponse = revokeDevice(user, deviceId);
        if (!revokeResponse.isSuccess()) {
            return revokeResponse;
        }

        long remaining = deviceRepository.countByUserAndIsActiveTrue(user);

        if (remaining <= maxSessionsPerUser) {
            return ApiResponse.success(
                    String.format("Device disconnected. You now have %d active device(s).", remaining));
        }

        return ApiResponse.success(
                String.format("Device disconnected. You still have %d active devices (limit: %d). " +
                        "Please disconnect another device.", remaining, maxSessionsPerUser));
    }

    // =========================================================================
    // STREAMING
    // =========================================================================

    @Transactional
    public ApiResponse<String> startStreaming(User user, UUID deviceId) {
        log.info("🎥 Starting streaming for device: {}", deviceId);

        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            return ApiResponse.error("Device not found");
        }

        DeviceEntity device = deviceOpt.get();
        if (!device.getUser().getId().equals(user.getId())) {
            return ApiResponse.error("Device not found");
        }

        if (device.isStreaming()) {
            return ApiResponse.error("This device is already streaming");
        }

        long streamingCount = deviceRepository.countByUserAndIsStreamingTrue(user);
        if (streamingCount >= maxStreamingSessions) {
            List<String> streamingOn = deviceRepository.findByUserAndIsActiveTrue(user).stream()
                    .filter(DeviceEntity::isStreaming)
                    .map(d -> d.getDeviceName() + " (" + maskIpAddress(d.getIpAddress()) + ")")
                    .collect(Collectors.toList());

            return ApiResponse.error(
                    "Streaming already active on: " + String.join(", ", streamingOn) +
                    ". Stop streaming there first.");
        }

        device.setStreaming(true);
        deviceRepository.save(device);

        log.info("✅ Streaming started on device: {}", deviceId);
        return ApiResponse.success("Streaming started");
    }

    @Transactional
    public ApiResponse<String> stopStreaming(User user, UUID deviceId) {
        log.info("⏹️ Stopping streaming for device: {}", deviceId);

        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            return ApiResponse.error("Device not found");
        }

        DeviceEntity device = deviceOpt.get();
        if (!device.getUser().getId().equals(user.getId())) {
            return ApiResponse.error("Device not found");
        }

        if (!device.isStreaming()) {
            return ApiResponse.error("This device is not currently streaming");
        }

        device.setStreaming(false);
        deviceRepository.save(device);

        log.info("✅ Streaming stopped on device: {}", deviceId);
        return ApiResponse.success("Streaming stopped");
    }

    // =========================================================================
    // CLEANUP (scheduled)
    // =========================================================================

    /**
     * Marks devices inactive and revokes their tokens/sessions if they haven't
     * been seen in 30 days. Runs daily at 2:30 AM (offset from token cleanup
     * at 2:00 AM). Uses a DB-level date filter instead of loading every device.
     */
    @Scheduled(cron = "0 30 2 * * ?")
    @Transactional
    public void cleanupInactiveDevices() {
        log.info("🧹 Starting cleanup of devices inactive for 30+ days");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<DeviceEntity> stale = deviceRepository.findByLastActiveAtBefore(cutoff);

        for (DeviceEntity device : stale) {
            device.setActive(false);
            device.setStreaming(false);
            refreshTokenRepository.revokeAllDeviceTokens(device.getId(), LocalDateTime.now());
            userSessionRepository.revokeDeviceSessions(device.getId(), LocalDateTime.now());
        }

        deviceRepository.saveAll(stale);
        log.info("✅ Cleaned up {} stale devices", stale.size());
    }

    // =========================================================================
    // PACKAGE-LEVEL HELPERS (used by other services)
    // =========================================================================

    public Optional<DeviceEntity> getCurrentDevice(User user, HttpServletRequest request) {
        String fp = generateDeviceFingerprint(request);
        return deviceRepository.findByUserAndDeviceFingerprint(user, fp);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Enforce the per-user device limit after a new device is registered.
     * Revokes the oldest active device(s) if the count exceeds the limit.
     * The newly registered device is excluded from auto-revocation.
     */
    private void enforceDeviceLimit(User user, DeviceEntity currentDevice) {
        List<DeviceEntity> active = deviceRepository.findByUserAndIsActiveTrue(user);

        if (active.size() <= maxSessionsPerUser) {
            return;
        }

        // Sort oldest-first; skip the device we just registered
        List<DeviceEntity> toRevoke = active.stream()
                .filter(d -> !d.getId().equals(currentDevice.getId()))
                .sorted(Comparator.comparing(DeviceEntity::getLastActiveAt))
                .limit(active.size() - maxSessionsPerUser)
                .collect(Collectors.toList());

        for (DeviceEntity old : toRevoke) {
            old.setActive(false);
            old.setStreaming(false);
            deviceRepository.save(old);
            refreshTokenRepository.revokeAllDeviceTokens(old.getId(), LocalDateTime.now());
            userSessionRepository.revokeDeviceSessions(old.getId(), LocalDateTime.now());
            log.info("🔒 Auto-revoked oldest device {} ({}) for user {} — limit enforced",
                    old.getId(), old.getDeviceName(), user.getEmail());
        }
    }

    private boolean isCurrentDevice(DeviceEntity device, HttpServletRequest request) {
        return device.getDeviceFingerprint().equals(generateDeviceFingerprint(request));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String generateDeviceName(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "Unknown Device";

        String browser;
        if      (ua.contains("Edg"))                                  browser = "Edge";
        else if (ua.contains("OPR") || ua.contains("Opera"))         browser = "Opera";
        else if (ua.contains("Chrome"))                               browser = "Chrome";
        else if (ua.contains("Firefox"))                              browser = "Firefox";
        else if (ua.contains("Safari"))                               browser = "Safari";
        else                                                          browser = "Unknown Browser";

        String os;
        if      (ua.contains("Android"))                              os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad"))       os = "iOS";
        else if (ua.contains("Windows NT 11.0"))                     os = "Windows 11";
        else if (ua.contains("Windows NT 10.0"))                     os = "Windows 10";
        else if (ua.contains("Windows"))                             os = "Windows";
        else if (ua.contains("Mac OS X"))                            os = "macOS";
        else if (ua.contains("Linux"))                               os = "Linux";
        else                                                         os = "Unknown OS";

        return browser + " on " + os;
    }

    private String maskIpAddress(String ip) {
        if (ip == null) return "unknown";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".***";
        }
        return ip; // IPv6 or unexpected format — return as-is
    }

    private String truncateUserAgent(String ua) {
        if (ua == null) return "";
        return ua.length() <= 80 ? ua : ua.substring(0, 77) + "...";
    }
}
